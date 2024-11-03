module aya.base {
  requires transitive aya.syntax;

  requires static org.jetbrains.annotations;

  // requires manifold.delegation.rt;

  exports org.aya.normalize;
  exports org.aya.prelude;
  exports org.aya.primitive;
  exports org.aya.resolve.context;
  exports org.aya.resolve.module;
  exports org.aya.resolve.salt;
  exports org.aya.resolve.visitor;
  exports org.aya.resolve;
  exports org.aya.tyck.ctx;
  exports org.aya.tyck.error;
  exports org.aya.tyck.tycker;
  exports org.aya.tyck;
  exports org.aya.unify;
  exports org.aya.resolve.error;
}
