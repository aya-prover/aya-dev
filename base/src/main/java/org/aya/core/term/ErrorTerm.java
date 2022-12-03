// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * @param isReallyError true if this is indeed an error,
 *                      false if this is just for pretty printing placeholder
 * @author ice1000
 * @see CoreDistiller#term(BaseDistiller.Outer, Term) (ErrorTerm case)
 */
public record ErrorTerm(@NotNull AyaDocile description, boolean isReallyError) implements StableWHNF {
  public ErrorTerm(@NotNull Term description) {
    this((AyaDocile) description.freezeHoles(null));
  }

  public ErrorTerm(@NotNull AyaDocile description) {
    this(description, true);
  }

  public ErrorTerm(@NotNull Doc description, boolean isReallyError) {
    this(options -> description, isReallyError);
  }

  public static @NotNull ErrorTerm typeOf(@NotNull Term origin) {
    return typeOf((AyaDocile) origin.freezeHoles(null));
  }

  public static @NotNull ErrorTerm typeOf(@NotNull AyaDocile origin) {
    return new ErrorTerm(options -> Doc.sep(
      Doc.plain("type of"),
      Doc.code(origin.toDoc(options))));
  }

  public static @NotNull ErrorTerm unexpected(@NotNull AyaDocile origin) {
    return new ErrorTerm(options -> Doc.sep(
      Doc.plain("unexpected"),
      Doc.code(origin.toDoc(options))));
  }
}
