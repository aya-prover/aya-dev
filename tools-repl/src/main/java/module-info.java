module org.aya.repl {
  requires static org.jetbrains.annotations;
  requires transitive kala.base;
  requires transitive kala.collection;
  requires org.aya.pretty;
  requires org.jline.reader;

  exports org.aya.repl;
}
