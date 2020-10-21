module org.mzi {
  requires static org.jetbrains.annotations;

  requires transitive org.mzi.api;
  requires transitive asia.kala.base;
  requires transitive asia.kala.collection;

  exports org.mzi.core.def;
  exports org.mzi.core.subst;
  exports org.mzi.core.term;
  exports org.mzi.core.sort;
  exports org.mzi.core.visitor;
  exports org.mzi.ref;
  exports org.mzi.generic;
  exports org.mzi.concrete.visitor;
  exports org.mzi.concrete.term;
  exports org.mzi.util;
  exports org.mzi.util.cancel;
}
