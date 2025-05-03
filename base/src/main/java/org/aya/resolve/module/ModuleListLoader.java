// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Result;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.LoadErrorKind;
import org.aya.resolve.error.ModNotFoundException;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record ModuleListLoader(
  @Override @NotNull Reporter reporter,
  @NotNull ImmutableSeq<? extends ModuleLoader> loaders
) implements ModuleLoader {
  @Override
  public @NotNull Result<ResolveInfo, LoadErrorKind> load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader) {
    for (var loader : loaders) {
      var loaded = loader.load(path, recurseLoader);
      if (loaded.isOk()) return loaded;
    }

    return Result.err(LoadErrorKind.NotFound);
  }

  @Override public boolean existsFileLevelModule(@NotNull ModulePath path) {
    return loaders.anyMatch(loader -> loader.existsFileLevelModule(path));
  }
}
