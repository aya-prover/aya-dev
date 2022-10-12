// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import org.aya.lsp.tester.LspTestClient;
import org.javacs.lsp.InitializeParams;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public abstract class LspTesterBase {
  public static final @NotNull Path TEST_LIB = Path.of("src", "test", "resources", "lsp-test-lib");

  public @NotNull LspTestClient launch(@NotNull Path libraryRoot) {
    var client = new LspTestClient();
    client.registerLibrary(libraryRoot);
    return client;
  }

  public @NotNull LspTestClient launch(InitializeParams params) {
    var client = new LspTestClient();
    client.service.initialize(params);
    return client;
  }
}
