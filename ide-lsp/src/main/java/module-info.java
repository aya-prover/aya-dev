module aya.ide.lsp {
  requires transitive aya.cli.console;
  requires aya.base;
  requires aya.ide;

  requires static org.jetbrains.annotations;
  requires com.google.gson;
  requires aya.javacs.protocol;
  requires info.picocli;

  exports org.aya.lsp.models;
  exports org.aya.lsp.server;
  exports org.aya.lsp.utils;
  exports org.aya.lsp;

  opens org.aya.lsp.models to com.google.gson;
  exports org.aya.lsp.actions;
}
