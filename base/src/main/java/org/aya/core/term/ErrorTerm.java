// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

/**
 * @param isReallyError true if this is indeed an error,
 *                      false if this is just for pretty printing placeholder
 * @author ice1000
 * @see CoreDistiller#visitError(ErrorTerm, Boolean)
 */
public record ErrorTerm(@NotNull Doc description, boolean isReallyError) implements Term {
  public ErrorTerm(@NotNull Doc description) {
    this(description, true);
  }

  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitError(this, p);
  }

  @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitError(this, p, q);
  }

  public static @NotNull ErrorTerm typeOf(@NotNull Doc origin) {
    return new ErrorTerm(Doc.sep(
      Doc.plain("type of"),
      Doc.styled(Style.code(), origin)));
  }

  public static @NotNull ErrorTerm unexpected(@NotNull Doc origin) {
    return new ErrorTerm(Doc.sep(
      Doc.plain("unexpected"),
      Doc.styled(Style.code(), origin)));
  }
}
