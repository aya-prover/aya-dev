module aya.util {
  requires aya.pretty;

  requires static org.jetbrains.annotations;
  requires transitive kala.base;
  requires transitive kala.collection;

  exports org.aya.util.cancel;
  exports org.aya.util.error;
  exports org.aya.util.binop;
  exports org.aya.util.reporter;
  exports org.aya.util.distill;
  exports org.aya.util.tyck;
  exports org.aya.util;
}
