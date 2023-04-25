module aya.md {
  requires aya.pretty;
  requires aya.util;

  requires org.commonmark;
  requires static org.jetbrains.annotations;

  exports org.aya.literate;
  exports org.aya.literate.parser;
  exports org.aya.literate.frontmatter;
  exports org.aya.literate.math;
}
