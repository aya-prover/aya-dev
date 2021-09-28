// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.server;

import org.aya.lsp.models.HighlightResult;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

public interface AyaLanguageClient extends LanguageClient {
  @JsonNotification("aya/publishSyntaxHighlight")
  void publishSyntaxHighlight(HighlightResult highlight);
}
