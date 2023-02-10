// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableTreeMap;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.resolve.ResolveInfo;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public class CachedModuleLoader<ML extends ModuleLoader> implements ModuleLoader {
  private final @NotNull MutableMap<@NotNull String, ResolveInfo> cache = MutableTreeMap.of();
  public final @NotNull ML loader;

  @Override public @NotNull Reporter reporter() {
    return loader.reporter();
  }

  public CachedModuleLoader(@NotNull ML loader) {
    this.loader = loader;
  }

  @Override public @Nullable ResolveInfo
  load(@NotNull ImmutableSeq<String> path, @NotNull ModuleLoader recurseLoader) {
    var qualified = QualifiedID.join(path);
    return cache.getOrPut(qualified, () -> loader.load(path, recurseLoader));
  }

  @Override
  public boolean existsFileLevelModule(@NotNull ImmutableSeq<@NotNull String> path) {
    return cache.containsKey(QualifiedID.join(path)) || loader.existsFileLevelModule(path);
  }
}
