module aya.compiler {
  requires aya.base;
  requires aya.util;
  requires kala.collection.primitive;

  requires static org.jetbrains.annotations;

  exports org.aya.compiler;
  exports org.aya.compiler.free;
  exports org.aya.compiler.free.data;
  exports org.aya.compiler.free.morphism;
  exports org.aya.compiler.serializers;
  exports org.aya.compiler.free.morphism.source;
}
