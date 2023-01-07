// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.prettier.BasePrettier;
import org.aya.prettier.CorePrettier;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @param isReallyError true if this is indeed an error,
 *                      false if this is just for pretty printing placeholder
 * @author ice1000
 * @see CorePrettier#term(BasePrettier.Outer, Term) (ErrorTerm case)
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

  private ErrorTerm update(AyaDocile description) {
    return description == description() ? this : new ErrorTerm(description, isReallyError);
  }
  @Override public @NotNull ErrorTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return description instanceof Term term ? update(term.descent(f)) : this;
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
