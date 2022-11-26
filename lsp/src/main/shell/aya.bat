@echo off
set DIR="%~dp0"
set JAVA_EXEC="%DIR:"=%\..\jre\bin\java"
set EXTRA_ARGS="--module-path=%~dp0\..\std\src"

REM pushd %DIR%
%JAVA_EXEC% %CDS_JVM_OPTS% --enable-preview -p "%~dp0/../app" -m aya.cli/org.aya.cli.Main %EXTRA_ARGS% %*
REM popd
