// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.distill;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record DistillerOptions(
  boolean showImplicitArgs,
  boolean showImplicitPats,
  boolean showLambdaTypes,
  boolean showLevels
) {
  public static final @NotNull DistillerOptions DEBUG =
    new DistillerOptions(true, true, true, true);
  public static final @NotNull DistillerOptions DEFAULT =
    new DistillerOptions(true, true, false, false);
}
