// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableTreeMap;
import kala.control.Result;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.error.LoadErrorKind;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public class CachedModuleLoader<ML extends ModuleLoader> implements ModuleLoader {
  private final @NotNull MutableMap<@NotNull String, ResolveInfo> cache = MutableTreeMap.of();
  public final @NotNull ML loader;

  @Override public @NotNull Reporter reporter() { return loader.reporter(); }
  public CachedModuleLoader(@NotNull ML loader) { this.loader = loader; }

  @Override public @NotNull Result<ResolveInfo, LoadErrorKind>
  load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader) {
    var qualified = path.toString();

    var cached = cache.getOrNull(qualified);
    if (cached != null) return Result.ok(cached);

    // we don't have `getOrPutChecked`, sorry
    var loaded = loader.load(path, recurseLoader);
    loaded.forEach(t -> cache.put(qualified, t));
    return loaded;
  }

  @Override public boolean existsFileLevelModule(@NotNull ModulePath path) {
    return cache.containsKey(path.toString()) || loader.existsFileLevelModule(path);
  }
}
