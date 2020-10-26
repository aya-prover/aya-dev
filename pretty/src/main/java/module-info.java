module org.mzi.pretty {
  requires org.jetbrains.annotations;

  requires transitive asia.kala.common;

  exports org.mzi.pretty.backend;
  exports org.mzi.pretty.doc;
  exports org.mzi.pretty.printer;
}
