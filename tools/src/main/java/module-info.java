module org.aya.util {
  requires static org.jetbrains.annotations;
  requires transitive kala.base;
  requires transitive kala.collection;
  requires org.aya.pretty;

  exports org.aya.util.cancel;
  exports org.aya.util.error;
  exports org.aya.util.binop;
  exports org.aya.util;
}
