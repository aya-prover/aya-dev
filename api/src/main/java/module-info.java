module org.mzi.api {
  requires static org.jetbrains.annotations;

  requires transitive asia.kala.base;
  requires transitive asia.kala.collection;

  exports org.mzi.api.core.def;
  exports org.mzi.api.core.term;
  exports org.mzi.api.error;
  exports org.mzi.api.ref;
  exports org.mzi.api.util;
}
