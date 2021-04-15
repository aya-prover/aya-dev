// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.util;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * @param <T> the type of expressions, can be {@link org.aya.api.core.CoreTerm} or
 *            {@link org.aya.api.concrete.ConcreteExpr}.
 * @author ice1000
 */
@Debug.Renderer(text = "toDoc().debugRender()")
public record Arg<T extends Docile>(@NotNull T term, boolean explicit) implements Docile {
  @Contract("_ -> new") public static <T extends Docile> @NotNull Arg<T> explicit(@NotNull T term) {
    return new Arg<>(term, true);
  }

  @Contract("_ -> new") public static <T extends Docile> @NotNull Arg<T> implicit(@NotNull T term) {
    return new Arg<>(term, false);
  }

  public @NotNull Arg<T> implicitify() {
    return new Arg<>(term, false);
  }

  @Override public @NotNull Doc toDoc() {
    var termDoc = term.toDoc();
    return explicit() ? termDoc : Doc.braced(termDoc);
  }
}
