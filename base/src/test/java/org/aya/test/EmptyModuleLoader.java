// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleLoader;
import org.aya.util.reporter.CollectingReporter;
import org.aya.util.reporter.IgnoringReporter;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@TestOnly
public enum EmptyModuleLoader implements ModuleLoader {
  @TestOnly INSTANCE(AyaThrowingReporter.INSTANCE),
  @TestOnly COLLECTING_ERRORS(CollectingReporter.delegate(IgnoringReporter.INSTANCE));

  private final @NotNull Reporter reporter;

  EmptyModuleLoader(@NotNull Reporter reporter) {this.reporter = reporter;}

  @Override public @NotNull Reporter reporter() {
    return reporter;
  }

  @Override public @Nullable ResolveInfo
  load(@NotNull ImmutableSeq<@NotNull String> path, @NotNull ModuleLoader recurseLoader) {
    return null;
  }

  @Override
  public boolean existsFileLevelModule(@NotNull ImmutableSeq<@NotNull String> path) {
    return false;
  }
}
