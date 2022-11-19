@echo off
set DIR="%~dp0"
set JAVA_EXEC="%DIR:"=%\jaya"

%JAVA_EXEC% %CDS_JVM_OPTS% --enable-preview -p "%~dp0/../app" -m aya.lsp/org.aya.lsp.LspMain %*
