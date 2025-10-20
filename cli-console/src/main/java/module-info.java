module aya.cli.console {
  requires transitive aya.cli.impl;
  requires transitive aya.repl;

  requires com.google.gson;
  requires info.picocli;
  requires org.jline.builtins;
  requires org.jline.reader;
  requires org.jline.terminal;
  requires java.net.http;
  requires aya.producer;
  requires com.google.errorprone.annotations;
  requires org.jetbrains.annotations;
  requires kala.common.gson;

  exports org.aya.cli.plct;
  exports org.aya.cli.repl;
  exports org.aya.cli.console;

  opens org.aya.cli.repl to aya.repl;
  opens org.aya.cli.plct to com.google.gson;
}
