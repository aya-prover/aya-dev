module aya.anf {
  requires aya.base;
  requires aya.util;
  requires kala.collection.primitive;

  requires static org.jetbrains.annotations;
  requires gradle.api;

  exports org.aya.anf.misc;
  exports org.aya.anf.ir.module;
  exports org.aya.anf.ir.struct;
}
