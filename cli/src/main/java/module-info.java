module org.aya.cli {
  requires info.picocli;
  requires com.google.gson;
  requires org.jetbrains.annotations;
  requires org.jline.terminal;
  requires org.jline.builtins;
  requires org.jline.terminal.jansi;
  requires org.jline.reader;
  requires transitive org.aya;
  requires transitive org.aya.repl;

  exports org.aya.cli;
  exports org.aya.cli.single;
  exports org.aya.cli.utils;
  exports org.aya.cli.library to com.google.gson;
  exports org.aya.cli.library.json to com.google.gson;
  exports org.aya.cli.repl;
}
