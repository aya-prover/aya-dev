// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.services.*;

import java.util.concurrent.CompletableFuture;

public class AyaServer implements LanguageClientAware, LanguageServer {
  @Override public void connect(LanguageClient client) {

  }

  @Override public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return null;
  }

  @Override public CompletableFuture<Object> shutdown() {
    return null;
  }

  @Override public void exit() {

  }

  @Override public TextDocumentService getTextDocumentService() {
    return null;
  }

  @Override public WorkspaceService getWorkspaceService() {
    return null;
  }
}
