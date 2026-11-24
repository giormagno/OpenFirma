@echo off
setlocal

pushd "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
set "EXITCODE=%ERRORLEVEL%"
popd

if not "%EXITCODE%"=="0" (
    echo.
    echo Errore durante l'avvio. Codice: %EXITCODE%
    pause
    exit /b %EXITCODE%
)

exit /b 0
