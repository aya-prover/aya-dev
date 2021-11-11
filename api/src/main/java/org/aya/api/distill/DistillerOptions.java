// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.distill;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class DistillerOptions {
  public boolean inlineMetas;
  public boolean showImplicitArgs;
  public boolean showImplicitPats;
  public boolean showLambdaTypes;
  public boolean showLevels;

  public DistillerOptions(
    boolean inlineMetas,
    boolean showImplicitArgs,
    boolean showImplicitPats,
    boolean showLambdaTypes,
    boolean showLevels
  ) {
    this.inlineMetas = inlineMetas;
    this.showImplicitArgs = showImplicitArgs;
    this.showImplicitPats = showImplicitPats;
    this.showLambdaTypes = showLambdaTypes;
    this.showLevels = showLevels;
  }

  @Contract(pure = true, value = "->new") public static @NotNull DistillerOptions debug() {
    return new DistillerOptions(false, true, true, true, true);
  }

  @Contract(pure = true, value = "->new") public static @NotNull DistillerOptions informative() {
    return new DistillerOptions(true, true, true, false, false);
  }

  @Contract(pure = true, value = "->new") public static @NotNull DistillerOptions pretty() {
    return new DistillerOptions(true, false, true, false, false);
  }
}
