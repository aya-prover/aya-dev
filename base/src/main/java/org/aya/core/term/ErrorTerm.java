// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.util.Decision;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record ErrorTerm(@NotNull Doc description) implements Term {
  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitError(this, p);
  }

  @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitError(this, p, q);
  }

  @Override public @NotNull Decision whnf() {
    return Decision.YES;
  }

  public static @NotNull ErrorTerm typeOf(@NotNull Doc origin) {
    return new ErrorTerm(Doc.hsep(
      Doc.plain("type of"),
      Doc.styled(Style.code(), origin)));
  }

  public static @NotNull ErrorTerm unexpected(@NotNull Doc origin) {
    return new ErrorTerm(Doc.hsep(
      Doc.plain("unexpected"),
      Doc.styled(Style.code(), origin)));
  }
}
