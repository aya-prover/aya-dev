// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record RecursiveSolutionError(
  @NotNull Var hole,
  @NotNull Term term,
  @NotNull SourcePos sourcePos
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Trying to solve hole `"),
      Doc.plain(hole.name()),
      Doc.plain("` as `"),
      term.toDoc(),
      Doc.plain("`, which is recursive"));
  }

  @Override public @NotNull Problem.Severity level() {
    return Severity.WARN;
  }
}
