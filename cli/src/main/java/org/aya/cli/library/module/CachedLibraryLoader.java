// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.module;

import org.aya.api.error.CountingReporter;
import org.aya.cli.library.LibraryCompiler;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.jetbrains.annotations.NotNull;

public class CachedLibraryLoader extends CachedModuleLoader<LibraryModuleLoader> implements LibraryModuleLoader {
  @Override public @NotNull CountingReporter reporter() {
    return compiler().reporter;
  }

  public CachedLibraryLoader(@NotNull LibraryModuleLoader loader) {
    super(loader);
  }

  @Override public @NotNull LibraryCompiler compiler() {
    return loader.compiler();
  }

  @Override public @NotNull LibraryModuleLoader.United states() {
    return loader.states();
  }
}
