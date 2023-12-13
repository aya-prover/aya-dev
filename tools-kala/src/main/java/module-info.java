module aya.util.kala {
  requires transitive aya.util;

  requires static org.jetbrains.annotations;
  requires transitive kala.collection.primitive;

  exports org.aya.util.tyck.pat;
  exports org.aya.util.terck;
}
