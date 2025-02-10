module aya.util {
  requires transitive aya.ij.util.text;
  requires transitive aya.pretty;

  requires static org.jetbrains.annotations;
  requires transitive kala.collection;

  exports org.aya.util.tyck;
  exports org.aya.util;
}
