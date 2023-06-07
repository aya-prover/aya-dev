module aya.cli.impl {
  requires transitive aya.parser.ij;
  requires transitive aya.base;

  requires static org.jetbrains.annotations;

  requires aya.ij.parsing.core;
  requires aya.ij.parsing.wrapper;
  requires com.google.gson;
  requires org.commonmark;

  exports org.aya.cli.interactive;
  exports org.aya.cli.library.incremental;
  exports org.aya.cli.library.json;
  exports org.aya.cli.library.source;
  exports org.aya.cli.library;
  exports org.aya.cli.literate;
  exports org.aya.cli.parse;
  exports org.aya.cli.render.vscode;
  exports org.aya.cli.render;
  exports org.aya.cli.single;
  exports org.aya.cli.utils;

  opens org.aya.cli.library.json to com.google.gson;
}
