// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.generic.util.InternalException;
import org.javacs.lsp.LspRequest;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class AyaTestLanguageServer extends AyaLanguageServer {
  public AyaTestLanguageServer(@NotNull CompilerAdvisor advisor, @NotNull AyaLanguageClient client) {
    super(advisor, client);
  }

  /**
   * A test only API, throws an InternalException
   */
  @Contract("-> fail")
  @LspRequest("aya/debug/panic")
  public void pleasePanic() {
    throw new InternalException("Sent!");
  }
}
