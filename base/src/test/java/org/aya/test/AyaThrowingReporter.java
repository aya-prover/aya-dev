// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test;

import org.aya.pretty.AyaPrettierOptions;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;

public class AyaThrowingReporter {
  public static final @NotNull ThrowingReporter INSTANCE = new ThrowingReporter(AyaPrettierOptions.informative());
}
