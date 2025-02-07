module aya.util {
  requires transitive aya.ij.util.text;
  requires transitive aya.pretty;

  requires static org.jetbrains.annotations;
  requires transitive kala.collection;
  requires transitive kala.collection.primitive;

  exports org.aya.util.error;
  exports org.aya.util.error.pretty;
  exports org.aya.util.prettier;
  exports org.aya.util.reporter;
  exports org.aya.util.tyck;
  exports org.aya.util;
}
