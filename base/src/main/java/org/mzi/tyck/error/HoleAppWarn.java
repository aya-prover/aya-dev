// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.error;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Error;
import org.mzi.core.term.HoleTerm;

public record HoleAppWarn(@NotNull HoleTerm term) implements Error {
  @Override public @NotNull Error.Severity level() {
    return Severity.WARN;
  }
}
