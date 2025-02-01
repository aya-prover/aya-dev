module aya.compiler {
  requires aya.base;
  requires aya.util;
  requires kala.collection.primitive;

  requires static org.jetbrains.annotations;
  requires org.glavo.classfile;

  exports org.aya.compiler;
  exports org.aya.compiler.free;
  exports org.aya.compiler.free.data;
  exports org.aya.compiler.serializers;
}
