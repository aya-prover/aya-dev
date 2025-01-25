// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.pat.MatcherBase;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Term;
import org.aya.tyck.ctx.LocalLet;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public final class PatBinder extends MatcherBase {
  private final LocalLet let = new LocalLet();
  public PatBinder() {
    super(UnaryOperator.identity());
  }
  @Override protected void onMetaPat(@NotNull Pat pat, MetaPatTerm metaPatTerm) {
    Panic.unreachable();
  }
  @Override protected void onMatchBind(Pat.Bind bind, @NotNull Term matched) {
    // This may seem like an optimization, but in reality it's completely useless.
    // if (matched instanceof FreeTerm(var name) && bind.bind() == name) return;
    let.put(bind.bind(), new Jdg.Default(matched, bind.type()));
  }
  public LocalLet apply(@NotNull ImmutableSeq<Pat> pats, @NotNull ImmutableSeq<Term> term) {
    try {
      matchMany(pats, term);
      return let;
    } catch (Failure e) {
      return Panic.unreachable();
    }
  }
}
