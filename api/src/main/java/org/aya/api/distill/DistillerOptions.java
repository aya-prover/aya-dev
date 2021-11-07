// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.distill;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record DistillerOptions(
  boolean inlineMetas,
  boolean showImplicitArgs,
  boolean showImplicitPats,
  boolean showLambdaTypes,
  boolean showLevels
) {
  public static final @NotNull DistillerOptions DEBUG =
    new DistillerOptions(false, true, true, true, true);
  public static final @NotNull DistillerOptions DEFAULT =
    new DistillerOptions(true, true, true, false, false);
  public static final @NotNull DistillerOptions PRETTY =
    new DistillerOptions(true, false, true, false, false);
}
