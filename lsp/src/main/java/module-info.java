module org.aya.lsp {
  requires static org.jetbrains.annotations;
  requires org.aya;
  requires org.aya.cli;
  requires org.eclipse.lsp4j;

  exports org.aya.lsp;
  exports org.aya.lsp.highlight;
  exports org.aya.lsp.language;
  exports org.aya.lsp.server;
  exports org.aya.lsp.definition;

  opens org.aya.lsp.language to com.google.gson;
  opens org.aya.lsp.highlight to com.google.gson;
  opens org.aya.lsp.definition to com.google.gson;
}
