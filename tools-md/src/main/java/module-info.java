module aya.md {
  requires aya.util;
  requires transitive aya.pretty;

  requires org.commonmark;
  requires static org.jetbrains.annotations;

  exports org.aya.literate;
  exports org.aya.literate.parser;
  exports org.aya.literate.frontmatter;
  exports org.aya.literate.math;
}
