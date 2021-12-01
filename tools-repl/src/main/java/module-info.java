module org.aya.repl {
  requires static org.jetbrains.annotations;
  requires transitive kala.base;
  requires transitive kala.collection;
  requires org.antlr.antlr4.runtime;
  requires org.aya.pretty;
  requires org.jline.reader;
  requires org.jline.terminal;

  exports org.aya.repl.antlr;
  exports org.aya.repl;
}
