@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "LOG=%~dp0sdk-log.txt"
set "SDK=%LOCALAPPDATA%\Android\Sdk"
echo Android SDK setup started %date% %time% > "%LOG%"
java -version >> "%LOG%" 2>&1

echo Installing the Android SDK into %SDK% ... (about 1 GB, please wait)
if not exist "%SDK%\cmdline-tools\latest\bin\sdkmanager.bat" (
  mkdir "%SDK%\cmdline-tools" 2>nul
  echo Downloading command-line tools...>> "%LOG%"
  curl.exe -L --fail -o "%TEMP%\rf-cmdline-tools.zip" https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip >> "%LOG%" 2>&1
  if errorlevel 1 ( echo DOWNLOAD_FAILED>> "%LOG%" & goto :end )
  rmdir /s /q "%SDK%\cmdline-tools\_tmp" 2>nul
  mkdir "%SDK%\cmdline-tools\_tmp"
  tar -xf "%TEMP%\rf-cmdline-tools.zip" -C "%SDK%\cmdline-tools\_tmp" >> "%LOG%" 2>&1
  move "%SDK%\cmdline-tools\_tmp\cmdline-tools" "%SDK%\cmdline-tools\latest" >> "%LOG%" 2>&1
  rmdir /s /q "%SDK%\cmdline-tools\_tmp" 2>nul
  del "%TEMP%\rf-cmdline-tools.zip" 2>nul
)
if not exist "%SDK%\cmdline-tools\latest\bin\sdkmanager.bat" ( echo SDKMANAGER_MISSING>> "%LOG%" & goto :end )

echo Accepting Android SDK licenses...>> "%LOG%"
mkdir "%SDK%\licenses" 2>nul
> "%SDK%\licenses\android-sdk-license" (
  echo.
  echo 8933bad161af4178b1185d1a37fbf41ea5269c55
  echo d56f5187479451eabf01fb78af6dfcb131a6481e
  echo 24333f8a63b6825ea9c5514f83c2829b004d1fee
)
> "%SDK%\licenses\android-sdk-preview-license" (
  echo.
  echo 84831b9409646a918e30573bab4c9c91346d8abd
)

echo Installing platform-tools, platforms;android-35, build-tools;35.0.0...>> "%LOG%"
call "%SDK%\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="%SDK%" "platform-tools" "platforms;android-35" "build-tools;35.0.0" < nul >> "%LOG%" 2>&1
echo SDKMANAGER_EXIT=!errorlevel!>> "%LOG%"
dir /b "%SDK%" >> "%LOG%" 2>&1
dir /b "%SDK%\platforms" >> "%LOG%" 2>&1

:end
echo SDK_SCRIPT_FINISHED>> "%LOG%"
timeout /t 10
