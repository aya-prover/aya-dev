// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public interface TyckError extends Problem {
  @Override default @NotNull Problem.Severity level() { return Severity.ERROR; }
  @Override default @NotNull Stage stage() { return Stage.TYCK; }
}
