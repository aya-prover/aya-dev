// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.concrete.Pattern;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record SplittingOnNonData(
  @NotNull Pattern pattern,
  @NotNull Term type
) implements PatternProblem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Cannot split on a non-inductive type `"),
      type.toDoc(),
      Doc.plain("` with a constructor pattern `"),
      pattern.toDoc(),
      Doc.plain("`")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
