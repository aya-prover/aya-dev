module aya.base {
  requires transitive aya.pretty;
  requires transitive aya.util;
  requires transitive aya.guest.cubical;
  requires transitive kala.base;
  requires transitive kala.collection;

  requires static org.jetbrains.annotations;

  requires aya.ij.parsing.core;
  requires org.commonmark;

  exports org.aya.concrete.desugar;
  exports org.aya.concrete.error;
  exports org.aya.concrete.remark;
  exports org.aya.concrete.stmt;
  exports org.aya.concrete.visitor;
  exports org.aya.concrete;
  exports org.aya.core.def;
  exports org.aya.core.ops;
  exports org.aya.core.pat;
  exports org.aya.core.repr;
  exports org.aya.core.serde;
  exports org.aya.core.term;
  exports org.aya.core.visitor;
  exports org.aya.core;
  exports org.aya.prettier;
  exports org.aya.generic.util;
  exports org.aya.generic;
  exports org.aya.tyck.tycker;
  // Needed by AyaProducer for reporting NotAnIntervalError
  exports org.aya.prelude;
  exports org.aya.ref;
  exports org.aya.resolve.context;
  exports org.aya.resolve.error;
  exports org.aya.resolve.module;
  exports org.aya.resolve;
  exports org.aya.tyck.env;
  exports org.aya.tyck.error;
  exports org.aya.tyck.order;
  exports org.aya.tyck.pat;
  exports org.aya.tyck.trace;
  exports org.aya.tyck.unify;
  exports org.aya.tyck;
    exports org.aya.tyck.covariance;
}
