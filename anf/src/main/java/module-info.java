module aya.anf {
  requires aya.base;
  requires aya.util;
  requires kala.collection.primitive;

  requires static org.jetbrains.annotations;

  exports org.aya.anf.ir;
  exports org.aya.anf.misc;
}
