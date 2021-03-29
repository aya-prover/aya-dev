// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.concrete.Pattern;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record UnknownCtorError(
  @NotNull Pattern pattern
) implements PatternProblem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Unknown constructor `"),
      pattern.toDoc(),
      Doc.plain("`")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
