// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.module;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.Var;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public record ModuleListLoader(
  @NotNull ImmutableSeq<? extends ModuleLoader> loaders) implements ModuleLoader {
  public ModuleListLoader(@NotNull ImmutableSeq<? extends @NotNull ModuleLoader> loaders) {
    this.loaders = loaders;
  }

  @Override
  public @Nullable
  MutableMap<Seq<String>, MutableMap<String, Var>> load(@NotNull Seq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    for (var loader : loaders) {
      var mod = loader.load(path, recurseLoader);
      if (mod != null) return mod;
    }
    return null;
  }
}
