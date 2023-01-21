@echo off
set DIR="%~dp0"
set JAVA_EXEC="%DIR:"=%\..\jre\bin\java"
set EXTRA_ARGS="--module-path=%~dp0\..\std\src"
set AYA_MODULE="aya.cli.console"
set AYA_MAIN="org.aya.cli.console.Main"

REM pushd %DIR%
%JAVA_EXEC% %CDS_JVM_OPTS% --enable-preview -p "%~dp0/../app" -m %AYA_MODULE%/%AYA_MAIN% %EXTRA_ARGS% %*
REM popd
