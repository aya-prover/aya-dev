module org.mzi.pretty {
  requires static org.jetbrains.annotations;
  requires static lombok;

  requires transitive org.glavo.kala.common;

  exports org.mzi.pretty.backend;
  exports org.mzi.pretty.doc;
  exports org.mzi.pretty.error;
  exports org.mzi.pretty.printer;
}
