// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.module;

import org.aya.api.ref.Var;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public final class ModuleListLoader implements ModuleLoader {
  @NotNull ImmutableSeq<@NotNull ModuleLoader> loaders;

  public ModuleListLoader(@NotNull ImmutableSeq<@NotNull ModuleLoader> loaders) {
    this.loaders = loaders;
  }

  @Override
  public @Nullable MutableMap<Seq<String>, MutableMap<String, Var>> load(@NotNull Seq<@NotNull String> path) {
    for (var loader : loaders) {
      var mod = loader.load(path);
      if (mod != null) return mod;
    }
    return null;
  }
}
