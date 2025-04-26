// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
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
  public @NotNull ResolveInfo load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader)
    throws Context.ResolvingInterruptedException, ModNotFoundException {
    for (var loader : loaders) {
      try {
        return loader.load(path, recurseLoader);
      } catch (Context.ResolvingInterruptedException | ModNotFoundException _) {
      }
    }

    throw new ModNotFoundException();
  }

  @Override public boolean existsFileLevelModule(@NotNull ModulePath path) {
    return loaders.anyMatch(loader -> loader.existsFileLevelModule(path));
  }
}
