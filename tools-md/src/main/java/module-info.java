module aya.md {
  requires aya.util;
  requires aya.util.more;
  requires transitive aya.pretty;

  requires static org.jetbrains.annotations;

  requires aya.jb.md;
  requires aya.jb.md.ij;

  exports org.aya.literate;
  exports org.aya.literate.parser;
}
