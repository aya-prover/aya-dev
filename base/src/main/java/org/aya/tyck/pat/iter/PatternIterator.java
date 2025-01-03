// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.util.Arg;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PatternIterator extends PusheenIterator<Arg<WithPos<Pattern>>, WithPos<Expr>> {
  public static final @NotNull Pusheenable<Arg<WithPos<Pattern>>, WithPos<Expr>> DUMMY =
    new ConstPusheen<>(WithPos.dummy(new Expr.Error(Doc.empty())));

  public PatternIterator(@NotNull ImmutableSeq<Arg<WithPos<Pattern>>> patterns) {
    super(patterns.iterator(), DUMMY);
  }

  public PatternIterator(ImmutableSeq<Arg<WithPos<Pattern>>> patterns, @NotNull Pusheenable<Arg<WithPos<Pattern>>, WithPos<Expr>> cat) {
    super(patterns.iterator(), cat);
  }

  public @Nullable WithPos<Expr> exprBody() {
    if (cat == DUMMY) return null;
    assert cat instanceof LambdaPusheen;
    return cat.body();
  }
}
