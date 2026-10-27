@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "LOG=%~dp0build-log.txt"
echo RupeeFlow build started %date% %time% > "%LOG%"

rem ---- Java: use Android Studio's bundled JDK if JAVA_HOME isn't set
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if not defined JAVA_HOME if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio1\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio1\jbr"
echo JAVA_HOME=!JAVA_HOME!>> "%LOG%"

rem ---- Android SDK location
if not exist local.properties (
  set "SDK="
  if exist "%LOCALAPPDATA%\Android\Sdk\platforms" set "SDK=%LOCALAPPDATA%\Android\Sdk"
  if defined ANDROID_HOME if not defined SDK set "SDK=%ANDROID_HOME%"
  if defined SDK (
    set "ESC=!SDK:\=\\!"
    > local.properties echo sdk.dir=!ESC!
  )
)
if exist local.properties (type local.properties >> "%LOG%") else (echo NO_SDK_FOUND looked in %LOCALAPPDATA%\Android\Sdk>> "%LOG%")
if exist "%LOCALAPPDATA%\Android\Sdk" dir /b "%LOCALAPPDATA%\Android\Sdk" >> "%LOG%" 2>&1

echo.
echo Building RupeeFlow... this can take 5-15 minutes the first time.
echo Progress is written to build-log.txt
echo.
rem ---- Signed release if you have the private key, otherwise a debug build anyone can make
set "TASK=assembleRelease"
set "OUT=app\build\outputs\apk\release\app-release.apk"
set "NAME=RupeeFlow-1.1.0.apk"
if not exist keystore.properties (
  set "TASK=assembleDebug"
  set "OUT=app\build\outputs\apk\debug\app-debug.apk"
  set "NAME=RupeeFlow-debug.apk"
  echo No keystore.properties found - building a DEBUG apk instead of a signed release.
  echo NO_KEYSTORE_DEBUG_BUILD>> "%LOG%"
)

call gradlew.bat :core:test testDebugUnitTest !TASK! --console=plain >> "%LOG%" 2>&1
set "RC=!errorlevel!"
echo EXIT_CODE=!RC!>> "%LOG%"

if "!RC!"=="0" (
  copy /y "!OUT!" "!NAME!" >nul
  certutil -hashfile "!NAME!" SHA256 >> "%LOG%"
  echo.
  echo SUCCESS - APK ready: %CD%\!NAME!
) else (
  echo.
  echo BUILD FAILED - open build-log.txt and look for lines starting with "e:" or "FAILURE"
)
echo BUILD_SCRIPT_FINISHED>> "%LOG%"
timeout /t 20
