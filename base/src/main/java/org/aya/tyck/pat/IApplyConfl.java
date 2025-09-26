// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.generic.Modifier;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.pat.PatMatcher;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.FnCall;
import org.aya.syntax.ref.MapLocalCtx;
import org.aya.tyck.error.ClausesProblem;
import org.aya.tyck.error.UnifyInfo;
import org.aya.tyck.tycker.Contextful;
import org.aya.tyck.tycker.Unifiable;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/// This is XTT-specific confluence check, very simple: we check for all combinations.
/// So, if we do
/// ```
/// def infix + (a b : Int) : Int
/// | zro i, zro j => u
///```
/// This thing will check the following:
///
/// - `zro 0, zro 0`
/// - `zro 0, zro 1`
/// - `zro 1, zro 1`
/// - `zro 1, zro 0`
///
/// In proper cubical type theory, we need to check `zro 0, zro j` and `zro i, zro 0`.
/// The latter looks like smaller number of checks, but honestly I don't know how to do it in terms of
/// pure patterns. The old version of Aya used a hack based on object identity, and I don't like it.
/// This one is translatable to a purely functional programming language.
public record IApplyConfl<Tycker extends Unifiable & Contextful>(
  @NotNull FnDef def, @NotNull ImmutableSeq<WithPos<Term.Matching>> matchings,
  boolean orderIndep, @NotNull SourcePos sourcePos, @NotNull Tycker tycker
) {
  public IApplyConfl(@NotNull FnDef def, @NotNull Tycker tycker, @NotNull SourcePos pos) {
    this(def, def.body().getRightValue().clauses, def.is(Modifier.Overlap), pos, tycker);
  }
  public void check() {
    // A matcher that does not normalize the arguments.
    var chillMatcher = new PatMatcher.NoMeta(UnaryOperator.identity());
    for (int i = 0, size = matchings.size(); i < size; i++) apply(i, chillMatcher);
  }

  private void apply(int i, PatMatcher.NoMeta chillMatcher) {
    var matching = matchings.get(i);
    var pats = matching.data().patterns().view();
    var ctx = new DimInPatsPermutation.CtxExtractinator(new MapLocalCtx(), MutableList.create());
    ctx.visit(pats);
    tycker.setLocalCtx(ctx.ctx());

    DimInPatsPermutation.forEach(pats, args -> {
      var nth = i + 1;
      doCompare(chillMatcher, args, matching, nth);
    });
  }

  private void doCompare(PatMatcher.NoMeta chillMatcher, ImmutableSeq<Term> args, WithPos<Term.Matching> matching, int nth) {
    var currentClause = chillMatcher.apply(matching.data(), args);
    var ano = new FnCall(new FnDef.Delegate(def.ref()), 0, args.map(tycker::whnf));
    tycker.unifyTermReported(ano, currentClause, def.result().instTele(args.view()),
      sourcePos, comparison -> new ClausesProblem.Conditions(
        sourcePos, matching.sourcePos(), nth, args, new UnifyInfo(tycker.state()), comparison));
  }
}
