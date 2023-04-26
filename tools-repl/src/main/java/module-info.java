module aya.repl {
  requires aya.util;
  requires transitive aya.pretty;

  requires static org.jetbrains.annotations;
  requires transitive kala.base;
  requires transitive kala.collection;
  requires org.jline.reader;
  requires org.jline.terminal;

  exports org.aya.repl;
}
