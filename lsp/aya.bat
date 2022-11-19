@echo off
set DIR="%~dp0"
set JAVA_EXEC="%DIR:"=%\jaya"

REM pushd %DIR%
%JAVA_EXEC% %CDS_JVM_OPTS% --enable-preview -p "%~dp0/../app" -m aya.cli/org.aya.cli.Main  %*
REM popd
