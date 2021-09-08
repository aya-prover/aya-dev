// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.distill.AyaDocile;
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
public record ErrorTerm(@NotNull AyaDocile description, boolean isReallyError) implements Term {
  public ErrorTerm(@NotNull Term description) {
    this((AyaDocile) description.freezeHoles(null));
  }

  public ErrorTerm(@NotNull AyaDocile description) {
    this(description, true);
  }

  public ErrorTerm(@NotNull Doc description, boolean isReallyError) {
    this(options -> description, isReallyError);
  }

  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitError(this, p);
  }

  public static @NotNull ErrorTerm typeOf(@NotNull Term origin) {
    return typeOf((AyaDocile) origin.freezeHoles(null));
  }

  public static @NotNull ErrorTerm typeOf(@NotNull AyaDocile origin) {
    return new ErrorTerm(options -> Doc.sep(
      Doc.plain("type of"),
      Doc.styled(Style.code(), origin.toDoc(options))));
  }

  public static @NotNull ErrorTerm unexpected(@NotNull AyaDocile origin) {
    return new ErrorTerm(options -> Doc.sep(
      Doc.plain("unexpected"),
      Doc.styled(Style.code(), origin.toDoc(options))));
  }
}
