// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.util;

import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * @param <T> the type of expressions, can be {@link org.aya.api.core.CoreTerm} or
 *            {@link org.aya.api.concrete.ConcreteExpr}.
 * @author ice1000
 */
public record Arg<T extends AyaDocile>(@NotNull T term, boolean explicit) implements AyaDocile {
  public @NotNull Arg<T> implicitify() {
    return new Arg<>(term, false);
  }

  @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return Doc.bracedUnless(term.toDoc(options), explicit);
  }
}
