// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.concrete.Pattern;
import org.aya.core.pat.Pat;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record MatchingOverIntervalError(
  @NotNull SourcePos sourcePos,
  @NotNull Pat.Prim pat
) implements PatternProblem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Cannot match over \"Interval\" primary type: `"),
      pat.toDoc(),
      //TODO[emanon]: find a better explanation
      Doc.plain("`, see https://agda.readthedocs.io/en/v2.6.1.3/language/cubical.html#the-interval-and-path-types for reason.")
    );
  }
  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
  @Override public @NotNull Pattern pattern() {
    return null;
  }
}
