module org.aya.cli {
  requires static org.jetbrains.annotations;

  requires org.antlr.antlr4.runtime;
  requires info.picocli;
  requires com.google.gson;
  requires org.fusesource.jansi;
  requires org.jline.builtins;
  requires org.jline.reader;
  requires org.jline.terminal.jansi;
  requires org.jline.terminal;

  requires transitive org.aya.parser;
  requires transitive org.aya.repl;
  requires transitive org.aya;

  exports org.aya.cli.library.json;
  exports org.aya.cli.library.source;
  exports org.aya.cli.library;
  exports org.aya.cli.parse;
  exports org.aya.cli.repl;
  exports org.aya.cli.single;
  exports org.aya.cli.utils;
  exports org.aya.cli;

  opens org.aya.cli.library.json to com.google.gson;
  opens org.aya.cli.repl to org.aya.repl;
}
