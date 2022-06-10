// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import org.aya.lsp.models.HighlightResult;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

public interface AyaLanguageClient extends LanguageClient {
  @JsonNotification("aya/publishSyntaxHighlight")
  @SuppressWarnings("unused")
  void publishSyntaxHighlight(HighlightResult highlight);
}
