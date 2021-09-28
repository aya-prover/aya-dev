module org.aya.lsp {
  requires static org.jetbrains.annotations;
  requires org.aya;
  requires org.aya.cli;
  requires org.eclipse.lsp4j;
  requires info.picocli;

  exports org.aya.lsp.models;
  exports org.aya.lsp.server;
  exports org.aya.lsp.utils;
  exports org.aya.lsp;

  opens org.aya.lsp.models to com.google.gson;
  exports org.aya.lsp.actions;
}
