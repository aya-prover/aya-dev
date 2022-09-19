module aya.cli {
  requires transitive aya.parser;
  requires transitive aya.repl;
  requires transitive aya.base;

  requires static org.jetbrains.annotations;

  requires com.google.gson;
  requires info.picocli;
  requires org.antlr.antlr4.runtime;
  requires org.fusesource.jansi;
  requires org.jline.builtins;
  requires org.jline.reader;
  requires org.jline.terminal.jansi;
  requires org.jline.terminal;
  requires java.net.http;
  requires java.base;

  exports org.aya.cli.library.incremental;
  exports org.aya.cli.library.json;
  exports org.aya.cli.library.source;
  exports org.aya.cli.library;
  exports org.aya.cli.parse;
  exports org.aya.cli.plct;
  exports org.aya.cli.repl;
  exports org.aya.cli.single;
  exports org.aya.cli.utils;
  exports org.aya.cli;

  opens org.aya.cli.library.json to com.google.gson;
  opens org.aya.cli.repl to aya.repl;
}
