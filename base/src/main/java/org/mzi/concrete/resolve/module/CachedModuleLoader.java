// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.module;

import asia.kala.collection.immutable.ImmutableSeq;
import asia.kala.collection.mutable.MutableHashMap;
import asia.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.concrete.resolve.context.Context;

/**
 * @author re-xyr
 */
public final class CachedModuleLoader implements ModuleLoader {
  @NotNull MutableMap<@NotNull String, Context> cache = new MutableHashMap<>();
  @NotNull ModuleLoader loader;

  CachedModuleLoader(@NotNull ModuleLoader loader) {
    this.loader = loader;
  }

  @Override
  public @Nullable Context unsafeLoad(@NotNull ImmutableSeq<String> path) {
    var stringifiedPath = path.joinToString(".");
    return cache.getOrElseGet(stringifiedPath, () -> {
      var ctx = loader.load(path);
      cache.put(stringifiedPath, ctx);
      return ctx;
    });
  }
}
