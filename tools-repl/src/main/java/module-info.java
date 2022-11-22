module aya.repl {
  requires aya.pretty;
  requires aya.util;

  requires static org.jetbrains.annotations;
  requires transitive kala.base;
  requires transitive kala.collection;
  requires org.jline.reader;
  requires org.jline.terminal;

  exports org.aya.repl;
}
