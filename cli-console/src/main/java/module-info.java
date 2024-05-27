module aya.cli.console {
  requires transitive aya.cli.impl;
  requires transitive aya.repl;

  requires static org.jetbrains.annotations;

  requires com.google.gson;
  requires info.picocli;
  requires org.fusesource.jansi;
  requires org.jline.builtins;
  requires org.jline.reader;
  requires org.jline.terminal.jansi;
  requires org.jline.terminal;
  requires java.net.http;
  requires jdk.crypto.ec;
  requires aya.producer;

  exports org.aya.cli.plct;
  exports org.aya.cli.repl;
  exports org.aya.cli.console;

  opens org.aya.cli.repl to aya.repl;
}
