param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$releaseAppDir = Join-Path $root "release\OpenFirma"
$workDir = Join-Path $root "package-work\portable"
$payloadZip = Join-Path $workDir "OpenFirmaPayload.zip"
$launcherSource = Join-Path $workDir "OpenFirmaPortableLauncher.cs"
$portableExe = Join-Path $root "OpenFirma-Portable.exe"

function Get-CSharpCompiler {
    $candidates = @(
        "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe",
        "$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\csc.exe"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "Impossibile trovare csc.exe di .NET Framework."
}

if ($Clean) {
    Remove-Item $portableExe,$workDir -Recurse -Force -ErrorAction SilentlyContinue
}

& (Join-Path $root "package.ps1") -Clean:$Clean

if (-not (Test-Path (Join-Path $releaseAppDir "OpenFirma.exe"))) {
    throw "Distribuzione Windows non trovata in $releaseAppDir. Esegui prima package.ps1."
}

New-Item -ItemType Directory -Force -Path $workDir | Out-Null
Remove-Item $payloadZip,$launcherSource -Force -ErrorAction SilentlyContinue

Write-Host "Creo il payload portabile..."
Compress-Archive -Path (Join-Path $releaseAppDir "*") -DestinationPath $payloadZip -CompressionLevel Optimal -Force

$launcherCode = @'
using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Security.Cryptography;
using System.Threading;
using System.Windows.Forms;

internal static class Program
{
    private const string ResourceName = "OpenFirmaPayload.zip";

    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            using (var payload = Assembly.GetExecutingAssembly().GetManifestResourceStream(ResourceName))
            {
                if (payload == null)
                {
                    throw new InvalidOperationException("Payload incorporato non trovato.");
                }

                string hash = ComputeSha256(payload);
                string cacheRoot = Path.Combine(Path.GetTempPath(), "OpenFirmaPortable");
                string appDir = Path.Combine(cacheRoot, hash.Substring(0, 16));
                string appExe = Path.Combine(appDir, "OpenFirma.exe");
                string mutexName = "OpenFirmaPortable-" + hash.Substring(0, 16);

                using (var mutex = new Mutex(false, mutexName))
                {
                    mutex.WaitOne();
                    try
                    {
                        if (!File.Exists(appExe))
                        {
                            ExtractPayload(appDir);
                        }
                    }
                    finally
                    {
                        mutex.ReleaseMutex();
                    }
                }

                var startInfo = new ProcessStartInfo();
                startInfo.FileName = appExe;
                startInfo.WorkingDirectory = appDir;
                startInfo.UseShellExecute = false;
                startInfo.Arguments = JoinArguments(args);

                Process.Start(startInfo);
                return 0;
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                "Impossibile avviare OpenFirma.\r\n\r\n" + ex.Message,
                "OpenFirma Portable",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
            return 1;
        }
    }

    private static string ComputeSha256(Stream stream)
    {
        stream.Position = 0;
        using (var sha256 = SHA256.Create())
        {
            byte[] hash = sha256.ComputeHash(stream);
            stream.Position = 0;
            return BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
        }
    }

    private static void ExtractPayload(string appDir)
    {
        string parentDir = Path.GetDirectoryName(appDir);
        Directory.CreateDirectory(parentDir);

        string tempDir = appDir + ".tmp-" + Guid.NewGuid().ToString("N");
        Directory.CreateDirectory(tempDir);

        try
        {
            using (var payload = Assembly.GetExecutingAssembly().GetManifestResourceStream(ResourceName))
            {
                ExtractZip(payload, tempDir);
            }

            if (Directory.Exists(appDir))
            {
                Directory.Delete(appDir, true);
            }

            Directory.Move(tempDir, appDir);
        }
        catch
        {
            if (Directory.Exists(tempDir))
            {
                Directory.Delete(tempDir, true);
            }

            throw;
        }
    }

    private static void ExtractZip(Stream payload, string targetDir)
    {
        string normalizedTarget = Path.GetFullPath(targetDir).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;

        using (var archive = new ZipArchive(payload, ZipArchiveMode.Read))
        {
            foreach (ZipArchiveEntry entry in archive.Entries)
            {
                string destination = Path.GetFullPath(Path.Combine(targetDir, entry.FullName));
                if (!destination.StartsWith(normalizedTarget, StringComparison.OrdinalIgnoreCase))
                {
                    throw new InvalidOperationException("Il payload contiene un percorso non valido.");
                }

                if (String.IsNullOrEmpty(entry.Name))
                {
                    Directory.CreateDirectory(destination);
                    continue;
                }

                Directory.CreateDirectory(Path.GetDirectoryName(destination));
                entry.ExtractToFile(destination, true);
            }
        }
    }

    private static string JoinArguments(string[] args)
    {
        if (args == null || args.Length == 0)
        {
            return "";
        }

        string[] quoted = new string[args.Length];
        for (int i = 0; i < args.Length; i++)
        {
            quoted[i] = QuoteArgument(args[i]);
        }

        return String.Join(" ", quoted);
    }

    private static string QuoteArgument(string arg)
    {
        if (String.IsNullOrEmpty(arg))
        {
            return "\"\"";
        }

        bool needsQuotes = arg.IndexOfAny(new[] { ' ', '\t', '\n', '\v', '"' }) >= 0;
        if (!needsQuotes)
        {
            return arg;
        }

        var builder = new System.Text.StringBuilder();
        builder.Append('"');

        int backslashes = 0;
        foreach (char c in arg)
        {
            if (c == '\\')
            {
                backslashes++;
                continue;
            }

            if (c == '"')
            {
                builder.Append('\\', backslashes * 2 + 1);
                builder.Append('"');
                backslashes = 0;
                continue;
            }

            builder.Append('\\', backslashes);
            backslashes = 0;
            builder.Append(c);
        }

        builder.Append('\\', backslashes * 2);
        builder.Append('"');
        return builder.ToString();
    }
}
'@

Set-Content -Path $launcherSource -Value $launcherCode -Encoding UTF8

$csc = Get-CSharpCompiler
Write-Host "Compilo il launcher autoestraente..."
& $csc `
    /nologo `
    /target:winexe `
    /optimize+ `
    /out:$portableExe `
    /resource:"$payloadZip,OpenFirmaPayload.zip" `
    /reference:System.Windows.Forms.dll `
    /reference:System.IO.Compression.dll `
    /reference:System.IO.Compression.FileSystem.dll `
    $launcherSource

if (-not (Test-Path $portableExe)) {
    throw "Creazione dell'eseguibile portabile fallita: $portableExe"
}

$sizeMb = [Math]::Round((Get-Item $portableExe).Length / 1MB, 2)
Write-Host ""
Write-Host "Versione portable creata."
Write-Host "Eseguibile: $portableExe"
Write-Host "Dimensione: $sizeMb MB"
