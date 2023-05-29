// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import org.aya.generic.AyaDocile;
import org.aya.generic.Nested;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractLetPrettier<Bind, Term extends AyaDocile, LetTerm extends Nested<Bind, Term, LetTerm>>
  implements LetPrettier<Bind, Term, LetTerm> {
  private final @NotNull BasePrettier<Term> basePrettier;

  public AbstractLetPrettier(@NotNull BasePrettier<Term> basePrettier, LetTerm helper) {
    this.basePrettier = basePrettier;
  }

  @Override
  public @NotNull Doc kwLet() {
    return Doc.styled(BasePrettier.KEYWORD, "let");
  }

  @Override
  public @NotNull Doc kwIn() {
    return Doc.styled(BasePrettier.KEYWORD, "in");
  }

  @Override
  public @NotNull Doc bar() {
    return Doc.symbol("|");
  }

  @Override
  public @NotNull Doc term(@NotNull Term term) {
    return basePrettier.term(BasePrettier.Outer.Free, term);
  }
}
