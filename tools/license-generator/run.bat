@echo off
setlocal
cd /d "%~dp0"

if not exist out\ferpro\generator\GeneratorApp.class (
  echo Lance d'abord build.bat
  pause
  exit /b 1
)

set "BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "BIN=%JAVA_HOME%\bin\"
if not defined BIN if exist "C:\Program Files\Android\Android Studio\jbr\bin\javaw.exe" set "BIN=C:\Program Files\Android\Android Studio\jbr\bin\"
if not defined BIN (
  where javaw >nul 2>nul
  if errorlevel 1 (
    echo ERREUR : Java est introuvable. Installe un JDK 17.
    pause
    exit /b 1
  )
)

start "" "%BIN%javaw" -cp out ferpro.generator.GeneratorApp