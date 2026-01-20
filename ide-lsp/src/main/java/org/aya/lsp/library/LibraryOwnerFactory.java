// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.library;

import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.lsp.library.internal.WsLibrary;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public interface LibraryOwnerFactory {
  @NotNull LibraryOwner disk(@NotNull LibraryConfig config) throws IOException;
  @NotNull LibraryOwner mock(@NotNull Path source);

  enum Default implements LibraryOwnerFactory {
    INSTANCE;

    @Override
    public @NotNull DiskLibraryOwner disk(@NotNull LibraryConfig config) throws IOException {
      return DiskLibraryOwner.from(config);
    }

    @Override
    public @NotNull LibraryOwner mock(@NotNull Path source) {
      return WsLibrary.mock(source);
    }
  }
}
