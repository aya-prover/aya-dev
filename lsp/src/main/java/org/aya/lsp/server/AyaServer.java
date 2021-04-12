// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.server;

import org.aya.lsp.Log;
import org.aya.lsp.language.AyaLanguageClient;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.*;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class AyaServer implements LanguageClientAware, LanguageServer {
  private final AyaService service = new AyaService();

  @Override public void connect(@NotNull LanguageClient client) {
    Log.init(((AyaLanguageClient) client));
  }

  @Override public @NotNull CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var cap = new ServerCapabilities();
      cap.setTextDocumentSync(TextDocumentSyncKind.None);
      cap.setCompletionProvider(new CompletionOptions(true, Collections.singletonList(
        "QWERTYUIOPASDFGHJKLZXCVBNM.qwertyuiopasdfghjklzxcvbnm+-*/_[]:")));
      cap.setDefinitionProvider(Either.forLeft(true));
      var workCap = new WorkspaceServerCapabilities();
      var workOps = new WorkspaceFoldersOptions();
      workOps.setSupported(true);
      workOps.setChangeNotifications(true);
      workCap.setWorkspaceFolders(workOps);
      cap.setWorkspace(workCap);

      var folders = params.getWorkspaceFolders();
      if (folders != null) folders.forEach(f ->
        service.registerLibrary(Path.of(URI.create(f.getUri()))));

      return new InitializeResult(cap);
    });
  }

  @Override public @NotNull CompletableFuture<Object> shutdown() {
    return CompletableFuture.completedFuture(null);
  }

  @Override public void exit() {
    Log.i("Goodbye");
  }

  @Override public @NotNull TextDocumentService getTextDocumentService() {
    return service;
  }

  @Override public @NotNull WorkspaceService getWorkspaceService() {
    return service;
  }
}
