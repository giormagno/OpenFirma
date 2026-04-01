param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArgs
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

& (Join-Path $root "build.ps1")

$classPathEntries = @((Join-Path $root "out")) + (Get-ChildItem -Path (Join-Path $root "lib") -Filter *.jar | Sort-Object Name | ForEach-Object { $_.FullName })
$classPath = $classPathEntries -join ";"

& java --add-modules jdk.crypto.cryptoki,jdk.crypto.mscapi -cp $classPath it.firmadigitale.AppLauncher @AppArgs
