// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.Reporter;
import org.aya.concrete.resolve.ResolveInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public record ModuleListLoader(
  @Override @NotNull Reporter reporter,
  @NotNull ImmutableSeq<? extends ModuleLoader> loaders
) implements ModuleLoader {
  @Override public @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path) {
    for (var loader : loaders) {
      var mod = loader.load(path);
      if (mod != null) return mod;
    }
    return null;
  }
}
