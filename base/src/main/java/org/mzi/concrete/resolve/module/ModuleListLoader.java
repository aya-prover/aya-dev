// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.module;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.concrete.resolve.context.Context;

public class ModuleListLoader implements ModuleLoader {
  ImmutableSeq<ModuleLoader> loaders;

  public ModuleListLoader(ImmutableSeq<ModuleLoader> loaders) {
    this.loaders = loaders;
  }

  @Override
  public @Nullable Context load(@NotNull ImmutableSeq<String> path) {
    for (var loader : loaders) {
      var mod = loader.load(path);
      if (mod != null) return mod;
    }
    return null;
  }
}
