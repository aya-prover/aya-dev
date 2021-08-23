// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.Var;
import org.aya.concrete.stmt.QualifiedID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public final class CachedModuleLoader implements ModuleLoader {
  final @NotNull MutableMap<@NotNull String, MutableMap<ImmutableSeq<String>, MutableMap<String, Var>>> cache = new MutableHashMap<>();
  final @NotNull ModuleLoader loader;

  public CachedModuleLoader(@NotNull ModuleLoader loader) {
    this.loader = loader;
  }

  @Override
  public @Nullable MutableMap<ImmutableSeq<String>, MutableMap<String, Var>>
  load(@NotNull ImmutableSeq<String> path, @NotNull ModuleLoader recurseLoader) {
    var stringifiedPath = QualifiedID.join(path);
    return cache.getOrElse(stringifiedPath, () -> {
      var ctx = loader.load(path, recurseLoader);
      cache.put(stringifiedPath, ctx);
      return ctx;
    });
  }
}
