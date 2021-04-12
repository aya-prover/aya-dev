// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class AyaService implements WorkspaceService, TextDocumentService {
  @Override public void didOpen(DidOpenTextDocumentParams params) {

  }

  @Override public void didChange(DidChangeTextDocumentParams params) {

  }

  @Override public void didClose(DidCloseTextDocumentParams params) {

  }

  @Override public void didSave(DidSaveTextDocumentParams params) {

  }

  @Override public void didChangeConfiguration(DidChangeConfigurationParams params) {

  }

  @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {

  }
}
