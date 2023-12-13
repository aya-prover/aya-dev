module aya.util.kala {
  requires transitive aya.util;

  requires static org.jetbrains.annotations;
  requires transitive kala.collection.primitive;

  exports org.aya.util.more;
  exports org.aya.util.terck;
  exports org.aya.util.tyck.pat;
}
