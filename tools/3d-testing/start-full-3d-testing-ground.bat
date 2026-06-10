@echo off
setlocal

set "MODEL_DIR=C:\Users\xhatf\OneDrive\Desktop\3d models dropoff"
set "SCENARIO=mothership"

if not "%~1"=="" set "MODEL_DIR=%~1"
if not "%~2"=="" set "SCENARIO=%~2"

set "EAGLES_3D_MODEL_DIR=%MODEL_DIR%"
set "GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT %GRADLE_OPTS%"

pushd "%~dp0..\.."
call gradlew.bat runFull3DTestingGround -Pfull3dScenario=%SCENARIO%
set EXIT_CODE=%ERRORLEVEL%
popd

exit /b %EXIT_CODE%
