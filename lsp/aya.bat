@echo off
set DIR="%~dp0"
set JAVA_EXEC="%DIR:"=%\java"

%JAVA_EXEC% %CDS_JVM_OPTS% -Duser.dir=%cd% --enable-preview -p "%~dp0/../app" -m aya.cli/org.aya.cli.Main %*
