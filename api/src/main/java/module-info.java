module org.aya.api {
  requires static org.jetbrains.annotations;

  requires org.glavo.kala.collection;

  requires org.aya.pretty;

  exports org.aya.api.concrete;
  exports org.aya.api.core.def;
  exports org.aya.api.core.term;
  exports org.aya.api.error;
  exports org.aya.api.ref;
  exports org.aya.api.util;
  exports org.aya.api;
}
