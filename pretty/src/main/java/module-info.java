module aya.pretty {
  requires static org.jetbrains.annotations;

  requires kala.base;
  requires kala.collection;

  exports org.aya.pretty.backend.latex;
  exports org.aya.pretty.backend.html;
  exports org.aya.pretty.backend.string.custom;
  exports org.aya.pretty.backend.string.style;
  exports org.aya.pretty.backend.string;
  exports org.aya.pretty.doc;
  exports org.aya.pretty.error;
  exports org.aya.pretty.printer;
  exports org.aya.pretty.style;
}
