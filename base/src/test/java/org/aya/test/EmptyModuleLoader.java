// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleLoader;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@TestOnly
public final class EmptyModuleLoader implements ModuleLoader {
  @TestOnly
  public static final @NotNull EmptyModuleLoader INSTANCE = new EmptyModuleLoader();

  @Override public @NotNull Reporter reporter() {
    return AyaThrowingReporter.INSTANCE;
  }

  private EmptyModuleLoader() {}

  @Override public @Nullable ResolveInfo
  load(@NotNull ImmutableSeq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    return null;
  }

  @Override
  public boolean existsFileLevelModule(@NotNull ImmutableSeq<@NotNull String> path) {
    return false;
  }
}
