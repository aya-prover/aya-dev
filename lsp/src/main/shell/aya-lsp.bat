@echo off
set DIR="%~dp0"
set JAVA_EXEC="%DIR:"=%\..\jre\bin\java"

%JAVA_EXEC% %CDS_JVM_OPTS% --enable-preview -p "%~dp0/../app" -m aya.vsc/org.aya.lsp.LspMain %*
