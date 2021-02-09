// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.module;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;

/**
 * @author re-xyr
 */
public final class CachedModuleLoader implements ModuleLoader {
  @NotNull MutableMap<@NotNull String, MutableMap<Seq<String>, MutableMap<String, Var>>> cache = new MutableHashMap<>();
  @NotNull ModuleLoader loader;

  CachedModuleLoader(@NotNull ModuleLoader loader) {
    this.loader = loader;
  }

  @Override
  public @Nullable MutableMap<Seq<String>, MutableMap<String, Var>> load(@NotNull Seq<String> path) {
    var stringifiedPath = path.joinToString("::");
    return cache.getOrElseGet(stringifiedPath, () -> {
      var ctx = loader.load(path);
      cache.put(stringifiedPath, ctx);
      return ctx;
    });
  }
}
