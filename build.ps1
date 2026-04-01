param(
    [switch]$BuildOnly
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$libDir = Join-Path $root "lib"
$srcDir = Join-Path $root "src\main\java"
$outDir = Join-Path $root "out"

New-Item -ItemType Directory -Force -Path $libDir | Out-Null
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$dependencies = @(
    @{
        File = "bcprov-jdk18on-1.78.1.jar"
        Url = "https://repo1.maven.org/maven2/org/bouncycastle/bcprov-jdk18on/1.78.1/bcprov-jdk18on-1.78.1.jar"
    },
    @{
        File = "bcpkix-jdk18on-1.78.1.jar"
        Url = "https://repo1.maven.org/maven2/org/bouncycastle/bcpkix-jdk18on/1.78.1/bcpkix-jdk18on-1.78.1.jar"
    },
    @{
        File = "bcutil-jdk18on-1.78.1.jar"
        Url = "https://repo1.maven.org/maven2/org/bouncycastle/bcutil-jdk18on/1.78.1/bcutil-jdk18on-1.78.1.jar"
    }
)

foreach ($dependency in $dependencies) {
    $target = Join-Path $libDir $dependency.File
    if (-not (Test-Path $target)) {
        Write-Host "Scarico $($dependency.File)..."
        Invoke-WebRequest -Uri $dependency.Url -OutFile $target
    }
}

$javaFiles = Get-ChildItem -Path $srcDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $javaFiles) {
    throw "Nessun sorgente Java trovato in $srcDir"
}

$classpath = (($dependencies | ForEach-Object { Join-Path $libDir $_.File }) -join ";")

Write-Host "Compilo i sorgenti..."
javac -encoding UTF-8 -cp $classpath -d $outDir $javaFiles

Write-Host "Build completato. Classi in $outDir"

if (-not $BuildOnly) {
    $classPathEntries = @($outDir) + ($dependencies | ForEach-Object { Join-Path $libDir $_.File })
    $runClasspath = $classPathEntries -join ";"
    Write-Host "Avvio l'applicazione..."
    & java --add-modules jdk.crypto.cryptoki,jdk.crypto.mscapi -cp $runClasspath it.firmadigitale.AppLauncher
} else {
    Write-Host "Build completata senza avvio GUI. Per aprire la GUI usa .\\run.ps1 oppure .\\build.ps1"
}
