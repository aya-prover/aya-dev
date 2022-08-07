// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @param <T> the type of expressions, can be {@link org.aya.core.term.Term} or
 *            {@link org.aya.concrete.Expr}.
 * @author ice1000
 */
public record Arg<T extends AyaDocile>(@NotNull T term, boolean explicit) implements AyaDocile {
  public @NotNull Arg<T> implicitify() {
    return new Arg<>(term, false);
  }

  @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return Doc.bracedUnless(term.toDoc(options), explicit);
  }

  public Arg<T> descent(Function<T, T> f) {
    var term = f.apply(term());
    if (term == term()) return this;
    return new Arg<>(term, explicit());
  }
}
