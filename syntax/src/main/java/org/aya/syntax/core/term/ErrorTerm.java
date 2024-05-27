// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.generic.AyaDocile;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.jetbrains.annotations.NotNull;

/**
 * @param isReallyError true if this is indeed an error,
 *                      false if this is just for pretty printing placeholder
 * @see org.aya.prettier.CorePrettier#term(BasePrettier.Outer, Term) (ErrorTerm case)
 */
public record ErrorTerm(AyaDocile description, boolean isReallyError) implements StableWHNF, TyckInternal {
  public static final @NotNull ErrorTerm DUMMY =  new ErrorTerm(Doc.plain("dummy"), false);

  public ErrorTerm(Doc doc, boolean isReallyError) { this(_ -> doc, isReallyError); }
  public ErrorTerm(AyaDocile desc) { this(desc, true); }
  public static @NotNull ErrorTerm typeOf(@NotNull Term origin) {
    return typeOf((AyaDocile) origin);
  }

  public static @NotNull ErrorTerm typeOf(@NotNull AyaDocile origin) {
    return new ErrorTerm(options -> Doc.sep(
      Doc.plain("type of"),
      Doc.code(origin.toDoc(options))), true);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) { return this; }
}
