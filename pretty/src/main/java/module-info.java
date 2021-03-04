module org.aya.pretty {
  requires static org.jetbrains.annotations;

  requires transitive org.glavo.kala.common;

  exports org.aya.pretty.backend;
  exports org.aya.pretty.doc;
  exports org.aya.pretty.error;
  exports org.aya.pretty.printer;
}
