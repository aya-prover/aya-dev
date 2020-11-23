module org.mzi.pretty {
  requires static org.jetbrains.annotations;
  requires static lombok;

  requires transitive asia.kala.common;

  exports org.mzi.pretty.backend;
  exports org.mzi.pretty.doc;
  exports org.mzi.pretty.printer;
}
