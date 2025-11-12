// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import org.aya.normalize.LetReplacer;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.pat.MatcherBase;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.Term;
import org.aya.tyck.ctx.LocalLet;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public final class PatBinder extends MatcherBase {
  private final LetReplacer let = new LetReplacer(new LocalLet());
  public PatBinder() {
    super(UnaryOperator.identity());
  }
  @Override protected void onMetaPat(@Closed @NotNull Pat pat, @Closed @NotNull MetaPatTerm metaPatTerm) {
    Panic.unreachable();
  }
  @Override protected void onMatchBind(Pat.@Closed @NotNull Bind bind, @Closed @NotNull Term matched) {
    if (matched instanceof FreeTerm(var name) && bind.bind() == name) return;
    @Closed var jdg = new Jdg.Default(matched, bind.type());
    let.let().put(bind.bind(), new LocalLet.DefinedAs(jdg.map(let),
      // `inline` will not be used
      false));
  }
  public LetReplacer apply(@NotNull ImmutableSeq<@Closed Pat> pats, @NotNull ImmutableSeq<@Closed Term> term) {
    try {
      matchMany(pats, term);
      return let;
    } catch (Failure e) {
      return Panic.unreachable();
    }
  }
}
