// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.core.term.CallTerm;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record HoleBadSpineWarn(
  @NotNull CallTerm.Hole term,
  @NotNull SourcePos sourcePos
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.plain("Can't perform pattern unification on hole with spine " + term.args() + ".");
  }

  @Override public @NotNull Problem.Severity level() {
    return Problem.Severity.WARN;
  }
}
