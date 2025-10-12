module aya.compiler {
  requires aya.base;
  requires aya.util;
  requires kala.collection.primitive;

  requires static org.jetbrains.annotations;
  requires org.glavo.classfile;

  exports org.aya.compiler;
  exports org.aya.compiler.serializers;
  exports org.aya.compiler.morphism;
  exports org.aya.compiler.morphism.ast;
}
