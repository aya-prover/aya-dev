module aya.cli.impl {
  requires transitive aya.parser.ij;
  requires transitive aya.base;

  requires aya.ij.parsing.core;
  requires com.google.gson;
  requires aya.md;
  requires aya.producer;
  requires aya.compiler;
  requires org.jetbrains.annotations;
  requires org.commonmark;

  exports org.aya.cli.interactive;
  exports org.aya.cli.library.incremental;
  exports org.aya.cli.library.json;
  exports org.aya.cli.library.source;
  exports org.aya.cli.library;
  exports org.aya.cli.literate;
  exports org.aya.cli.render.vscode;
  exports org.aya.cli.render;
  exports org.aya.cli.single;
  exports org.aya.cli.utils;

  opens org.aya.cli.library.json to com.google.gson;
}
