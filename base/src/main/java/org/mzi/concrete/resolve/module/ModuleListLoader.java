// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.module;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.concrete.resolve.context.Context;

/**
 * @author re-xyr
 */
public class ModuleListLoader implements ModuleLoader {
  @NotNull ImmutableSeq<@NotNull ModuleLoader> loaders;

  public ModuleListLoader(@NotNull ImmutableSeq<@NotNull ModuleLoader> loaders) {
    this.loaders = loaders;
  }

  @Override
  public @Nullable Context load(@NotNull ImmutableSeq<@NotNull String> path) {
    for (var loader : loaders) {
      var mod = loader.load(path);
      if (mod != null) return mod;
    }
    return null;
  }
}
