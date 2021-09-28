module org.aya.api {
  requires static org.jetbrains.annotations;

  requires kala.collection;

  requires org.aya.pretty;

  exports org.aya.api.concrete;
  exports org.aya.api.core;
  exports org.aya.api.distill;
  exports org.aya.api.error;
  exports org.aya.api.ref;
  exports org.aya.api.util;
  exports org.aya.api;
}
