// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.util.Arg;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LambdaPusheen implements Pusheenable<Arg<WithPos<Pattern>>, @NotNull WithPos<Expr>> {
  private @NotNull WithPos<Expr> body;
  private @Nullable Arg<WithPos<Pattern>> peek;

  public LambdaPusheen(@NotNull WithPos<Expr> body) {
    this.body = body;
  }

  @Override public @NotNull Arg<WithPos<Pattern>> peek() {
    if (peek != null) return peek;

    var bind = ((Expr.Lambda) body.data()).ref();
    peek = Arg.ofExplicitly(new WithPos<>(bind.definition(), new Pattern.Bind(bind)));
    return peek;
  }

  @Override public @NotNull WithPos<Expr> body() { return body; }
  @Override public boolean hasNext() { return body.data() instanceof Expr.Lambda; }

  @Override public Arg<WithPos<Pattern>> next() {
    if (peek == null) peek();
    body = ((Expr.Lambda) body.data()).body();

    var result = peek;
    peek = null;
    return result;
  }
}
