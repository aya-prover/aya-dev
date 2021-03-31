module org.aya.pretty {
  requires static org.jetbrains.annotations;

  requires org.glavo.kala.base;
  requires org.glavo.kala.collection;

  exports org.aya.pretty.backend.html.style;
  exports org.aya.pretty.backend.html;
  exports org.aya.pretty.backend.string.custom;
  exports org.aya.pretty.backend.string.style;
  exports org.aya.pretty.backend.string;
  exports org.aya.pretty.doc;
  exports org.aya.pretty.error;
  exports org.aya.pretty.printer;
}
