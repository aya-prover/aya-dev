@echo off
set DIR="%~dp0"
set JAVA_EXEC="%DIR:"=%\..\jre\bin\java"
set CDS_JVM_OPTS=%CDS_JVM_OPTS% -XX:+UseCompactObjectHeaders ^
  --enable-native-access=org.jline.terminal.ffm ^
  --enable-native-access=org.jline.nativ

%JAVA_EXEC% %CDS_JVM_OPTS% --enable-preview -p "%~dp0/../app" -m aya.ide.lsp/org.aya.lsp.LspMain %*
