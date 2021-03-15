// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.concrete.Expr;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @param pats faked expressions to simulate patterns
 */
public record MissingCaseError(
  @NotNull SourcePos sourcePos,
  @NotNull Buffer<Expr> pats
) implements Problem.Error {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Unhandled case: "),
      Doc.join(Doc.plain(", "), pats.stream().map(Expr::toDoc))
    );
  }
}
