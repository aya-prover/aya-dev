// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import kala.tuple.Unit;
import org.aya.concrete.Pattern;
import org.aya.core.term.ErrorTerm;
import org.aya.tyck.LocalCtx;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @apiNote do not instantiate directly, use the provided static APIs
 * @see PatBindCollector#bindErrors(Pattern, LocalCtx)
 */
public record PatBindCollector(@NotNull ErrorTerm error) implements Pattern.Visitor<LocalCtx, Unit> {
  public static void bindErrors(@NotNull Pattern ctor, @NotNull LocalCtx ctx) {
    ctor.accept(new PatBindCollector(new ErrorTerm(ctor)), ctx);
  }

  @Override public Unit visitAbsurd(Pattern.@NotNull Absurd absurd, LocalCtx pats) {
    return Unit.unit();
  }

  @Override public Unit visitCalmFace(Pattern.@NotNull CalmFace calmFace, LocalCtx pats) {
    return Unit.unit();
  }

  @Override public Unit visitBind(Pattern.@NotNull Bind bind, LocalCtx pats) {
    pats.put(bind.bind(), error);
    return Unit.unit();
  }

  @Override public Unit visitTuple(Pattern.@NotNull Tuple tuple, LocalCtx pats) {
    if (tuple.as() != null) pats.put(tuple.as(), error);
    tuple.patterns().forEach(pat -> pat.accept(this, pats));
    return Unit.unit();
  }

  @Override public Unit visitCtor(Pattern.@NotNull Ctor ctor, LocalCtx pats) {
    if (ctor.as() != null) pats.put(ctor.as(), error);
    ctor.params().forEach(pat -> pat.accept(this, pats));
    return Unit.unit();
  }

  @Override public Unit visitNumber(Pattern.@NotNull Number number, LocalCtx pats) {
    return Unit.unit();
  }
}
