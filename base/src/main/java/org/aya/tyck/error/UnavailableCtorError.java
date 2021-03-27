// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.concrete.Pattern;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record UnavailableCtorError(
  @NotNull Pattern pattern,
  @NotNull Severity level
) implements Problem {
  @Override public @NotNull SourcePos sourcePos() {
    return pattern.sourcePos();
  }

  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Cannot match with `"),
      pattern.toDoc(),
      Doc.plain("` due to a failed index unification"),
      level == Severity.ERROR ? Doc.empty() : Doc.plain(", treating as bind pattern")
    );
  }
}
