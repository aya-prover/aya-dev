module org.mzi {
  requires static org.jetbrains.annotations;
  requires static org.antlr.antlr4.runtime;

  requires transitive org.mzi.api;
  requires transitive org.mzi.parser;
  requires transitive org.glavo.kala.common;

  requires org.mzi.pretty;

  exports org.mzi.concrete.desugar;
  exports org.mzi.concrete.parse;
  exports org.mzi.concrete.resolve.context;
  exports org.mzi.concrete.resolve.module;
  exports org.mzi.concrete.visitor;
  exports org.mzi.concrete;
  exports org.mzi.core.def;
  exports org.mzi.core.term;
  exports org.mzi.core.visitor;
  exports org.mzi.core;
  exports org.mzi.generic;
  exports org.mzi.ref;
  exports org.mzi.tyck.sort;
  exports org.mzi.tyck.unify;
  exports org.mzi.tyck;
  exports org.mzi.util.cancel;
  exports org.mzi.util;
}
