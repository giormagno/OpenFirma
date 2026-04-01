param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$outDir = Join-Path $root "out"
$libDir = Join-Path $root "lib"
$releaseDir = Join-Path $root "release"
$packageWorkDir = Join-Path $root "package-work"
$inputDir = Join-Path $packageWorkDir "input"
$appJar = Join-Path $inputDir "JavaSign.jar"

function Get-JdkBinDir {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin"
        if (Test-Path (Join-Path $candidate "jpackage.exe")) {
            return $candidate
        }
    }

    $installedJdks = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\jpackage.exe") } |
        Sort-Object Name -Descending

    if ($installedJdks) {
        return (Join-Path $installedJdks[0].FullName "bin")
    }

    throw "Impossibile trovare jpackage.exe. Installa un JDK con jpackage oppure imposta JAVA_HOME."
}

if ($Clean) {
    Remove-Item $releaseDir,$packageWorkDir -Recurse -Force -ErrorAction SilentlyContinue
}

& (Join-Path $root "build.ps1") -BuildOnly

$jdkBinDir = Get-JdkBinDir
$jarExe = Join-Path $jdkBinDir "jar.exe"
$jpackageExe = Join-Path $jdkBinDir "jpackage.exe"
$jdepsExe = Join-Path $jdkBinDir "jdeps.exe"

New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null

Remove-Item $appJar -Force -ErrorAction SilentlyContinue
Get-ChildItem -Path $inputDir -Filter *.jar -ErrorAction SilentlyContinue | Remove-Item -Force

Write-Host "Creo il jar applicativo..."
& $jarExe --create --file $appJar --main-class it.firmadigitale.AppLauncher -C $outDir .

Write-Host "Copio le dipendenze..."
Copy-Item (Join-Path $libDir "*.jar") $inputDir -Force

$classPath = @(
    Join-Path $inputDir "bcpkix-jdk18on-1.78.1.jar"
    Join-Path $inputDir "bcprov-jdk18on-1.78.1.jar"
    Join-Path $inputDir "bcutil-jdk18on-1.78.1.jar"
) -join ";"

$moduleDeps = & $jdepsExe --multi-release 21 --ignore-missing-deps --print-module-deps --class-path $classPath $appJar
$runtimeModules = ($moduleDeps.Split(",") + @("jdk.crypto.cryptoki", "jdk.crypto.mscapi") | Select-Object -Unique) -join ","

Write-Host "Modulo runtime inclusi: $runtimeModules"
Write-Host "Genero l'app Windows..."

Remove-Item (Join-Path $releaseDir "JavaSign") -Recurse -Force -ErrorAction SilentlyContinue

& $jpackageExe `
    --type app-image `
    --name "JavaSign" `
    --dest $releaseDir `
    --input $inputDir `
    --main-jar "JavaSign.jar" `
    --main-class "it.firmadigitale.AppLauncher" `
    --vendor "Codex" `
    --app-version "0.1.0" `
    --description "Desktop app per firma digitale P7M con certificati QES su Windows-MY" `
    --add-modules $runtimeModules `
    --java-options "--add-modules=jdk.crypto.cryptoki,jdk.crypto.mscapi"

$exePath = Join-Path $releaseDir "JavaSign\JavaSign.exe"
if (-not (Test-Path $exePath)) {
    throw "Packaging completato senza trovare l'eseguibile atteso: $exePath"
}

Write-Host ""
Write-Host "Packaging completato."
Write-Host "Eseguibile: $exePath"
