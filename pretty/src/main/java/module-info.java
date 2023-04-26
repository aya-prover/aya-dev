module aya.pretty {
  requires static org.jetbrains.annotations;

  requires transitive kala.base;
  requires transitive kala.collection;

  exports org.aya.pretty.backend.latex;
  exports org.aya.pretty.backend.html;
  exports org.aya.pretty.backend.md;
  exports org.aya.pretty.backend.string;
  exports org.aya.pretty.backend.terminal;
  exports org.aya.pretty.doc;
  exports org.aya.pretty.error;
  exports org.aya.pretty.printer;
  exports org.aya.pretty.style;
}
