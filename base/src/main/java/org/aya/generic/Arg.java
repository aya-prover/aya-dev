// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.pretty.doc.Doc;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @param <T> the type of expressions, can be {@link org.aya.core.term.Term} or
 *            {@link org.aya.concrete.Expr}.
 * @author ice1000
 */
@Debug.Renderer(text = "toDoc(DistillerOptions.debug(), this).debugRender()")
public record Arg<T>(@NotNull T term, boolean explicit) {
  public static <T extends AyaDocile> @NotNull Doc toDoc(@NotNull DistillerOptions options, @NotNull Arg<T> self) {
    return Doc.bracedUnless(self.term.toDoc(options), self.explicit);
  }

  public @NotNull Arg<T> update(@NotNull T term) {
    return term == term() ? this : new Arg<>(term, explicit);
  }

  public @NotNull Arg<T> descent(@NotNull Function<@NotNull T, @NotNull T> f) {
    return update(f.apply(term));
  }
}
