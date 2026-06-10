@echo off
setlocal

call "%~dp0tools\3d-testing\start-full-3d-testing-ground.bat" %*
exit /b %ERRORLEVEL%
