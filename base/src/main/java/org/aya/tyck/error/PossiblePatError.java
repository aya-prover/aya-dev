// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.concrete.Pattern;
import org.aya.core.term.CallTerm;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record PossiblePatError(
  @NotNull Pattern pattern,
  @NotNull CallTerm.ConHead available
) implements PatternProblem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Absurd pattern does not fit here because `"),
      Doc.plain(available.ref().name()),
      Doc.plain("` is still avaialble")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
