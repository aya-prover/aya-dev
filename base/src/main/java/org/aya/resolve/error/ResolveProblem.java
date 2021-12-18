// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import org.aya.api.error.Problem;
import org.jetbrains.annotations.NotNull;

public interface ResolveProblem extends Problem {
  @Override default @NotNull Stage stage() {
    return Stage.RESOLVE;
  }
}
