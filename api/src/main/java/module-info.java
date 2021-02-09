module org.mzi.api {
  requires static org.jetbrains.annotations;

  requires transitive org.glavo.kala.common;

  requires org.mzi.pretty;

  exports org.mzi.api.concrete.def;
  exports org.mzi.api.core.def;
  exports org.mzi.api.core.term;
  exports org.mzi.api.error;
  exports org.mzi.api.ref;
  exports org.mzi.api.util;
  exports org.mzi.api;
}
