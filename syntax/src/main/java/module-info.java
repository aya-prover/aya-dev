module aya.syntax {
  requires transitive aya.ij.parsing.core;
  requires transitive aya.md;
  requires transitive aya.pretty;
  requires transitive aya.util.more;
  requires transitive aya.util;
  requires transitive kala.base;
  requires transitive kala.collection;

  requires static org.jetbrains.annotations;
  requires org.commonmark;

  exports org.aya.generic;
  exports org.aya.prettier;
  exports org.aya.syntax.compile;
  exports org.aya.syntax.concrete.stmt.decl;
  exports org.aya.syntax.concrete.stmt;
  exports org.aya.syntax.concrete;
  exports org.aya.syntax.core.def;
  exports org.aya.syntax.core.pat;
  exports org.aya.syntax.core.repr;
  exports org.aya.syntax.core.term.call;
  exports org.aya.syntax.core.term.marker;
  exports org.aya.syntax.core.term.repr;
  exports org.aya.syntax.core.term.xtt;
  exports org.aya.syntax.core.term;
  exports org.aya.syntax.core;
  exports org.aya.syntax.literate;
  exports org.aya.syntax.ref;
  exports org.aya.syntax;
  exports org.aya.generic.term;
  exports org.aya.generic.stmt;
}
