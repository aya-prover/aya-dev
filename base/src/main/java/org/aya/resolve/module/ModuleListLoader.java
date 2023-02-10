// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.ResolveInfo;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public record ModuleListLoader(
  @Override @NotNull Reporter reporter,
  @NotNull ImmutableSeq<? extends ModuleLoader> loaders
) implements ModuleLoader {
  @Override
  public @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    for (var loader : loaders) {
      var mod = loader.load(path, recurseLoader);
      if (mod != null) return mod;
    }
    return null;
  }

  @Override
  public boolean exists(@NotNull ImmutableSeq<@NotNull String> path) {
    return loaders.anyMatch(loader -> loader.exists(path));
  }
}
