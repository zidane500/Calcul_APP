@echo off
setlocal
cd /d "%~dp0"

rem --- Choisir le JDK : JAVA_HOME, sinon Android Studio, sinon le PATH ---
set "BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "BIN=%JAVA_HOME%\bin\"
if not defined BIN if exist "C:\Program Files\Android\Android Studio\jbr\bin\javac.exe" set "BIN=C:\Program Files\Android\Android Studio\jbr\bin\"
if not defined BIN (
  where javac >nul 2>nul
  if errorlevel 1 (
    echo ERREUR : javac est introuvable. Installe un JDK 17 ^(Temurin^) puis relance.
    pause
    exit /b 1
  )
)
echo Compilation avec : %BIN%javac

if exist out rmdir /s /q out
mkdir out

"%BIN%javac" -encoding UTF-8 -d out -sourcepath ..\..\app\src\main\java src\ferpro\generator\KeyVault.java src\ferpro\generator\LicenseService.java src\ferpro\generator\GeneratorWindow.java src\ferpro\generator\GeneratorApp.java
if errorlevel 1 (
  echo.
  echo ERREUR de compilation.
  pause
  exit /b 1
)

echo.
echo OK : le generateur est compile. Lance maintenant run.bat
pause