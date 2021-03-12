// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.module;

import org.aya.api.ref.Var;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public final class CachedModuleLoader implements ModuleLoader {
  @NotNull MutableMap<@NotNull String, MutableMap<Seq<String>, MutableMap<String, Var>>> cache = new MutableHashMap<>();
  @NotNull ModuleLoader loader;

  public CachedModuleLoader(@NotNull ModuleLoader loader) {
    this.loader = loader;
  }

  @Override
  public @Nullable MutableMap<Seq<String>, MutableMap<String, Var>> load(@NotNull Seq<String> path, @NotNull ModuleLoader recurseLoader) {
    var stringifiedPath = path.joinToString("::");
    return cache.getOrElse(stringifiedPath, () -> {
      var ctx = loader.load(path, recurseLoader);
      cache.put(stringifiedPath, ctx);
      return ctx;
    });
  }
}
