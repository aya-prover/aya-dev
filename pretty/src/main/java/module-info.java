module org.mzi.pretty {
  requires org.jetbrains.annotations;

  requires transitive asia.kala.base;
  requires transitive asia.kala.collection;

  exports org.mzi.pretty.backend;
  exports org.mzi.pretty.doc;
  exports org.mzi.pretty.printer;
}
