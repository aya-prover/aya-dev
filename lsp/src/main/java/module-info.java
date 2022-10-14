module aya.lsp {
  requires aya.base;
  requires aya.cli;

  requires static org.jetbrains.annotations;
  requires com.google.gson;
  requires aya.javacs.protocol;
  requires info.picocli;

  exports org.aya.lsp.models;
  exports org.aya.lsp.server;
  exports org.aya.lsp.prim;
  exports org.aya.lsp.utils;
  exports org.aya.lsp;

  opens org.aya.lsp.models to com.google.gson;
  exports org.aya.lsp.actions;
  exports org.aya.lsp.options;
  opens org.aya.lsp.options to com.google.gson;
}
