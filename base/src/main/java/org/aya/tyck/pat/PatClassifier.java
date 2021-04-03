// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.ConfluenceError;
import org.aya.tyck.error.MissingCaseError;
import org.aya.util.Ordering;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.tuple.primitive.IntObjTuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000, kiva
 */
public record PatClassifier(
  @NotNull Reporter reporter,
  @NotNull SourcePos pos,
  @NotNull PatTree.Builder builder
) {
  public static @NotNull ImmutableSeq<PatClass> classify(
    @NotNull ImmutableSeq<Pat.@NotNull PrototypeClause> clauses,
    @NotNull Reporter reporter, @NotNull SourcePos pos, boolean coverage
  ) {
    var classifier = new PatClassifier(reporter, pos, new PatTree.Builder());
    return classifier.classifySub(clauses.mapIndexed((index, clause) ->
      new SubPats(clause.patterns(), index)), coverage);
  }

  public static void confluence(
    @NotNull ImmutableSeq<Pat.@NotNull PrototypeClause> clauses,
    @NotNull ExprTycker tycker, @NotNull SourcePos pos,
    @NotNull Term result, @NotNull ImmutableSeq<PatClass> classification
  ) {
    for (var results : classification) {
      var contents = results.contents.flatMap(i ->
        Pat.PrototypeClause.deprototypify(clauses.get(i)).map(matching -> IntObjTuple2.of(i, matching)));
      for (int i = 1, size = contents.size(); i < size; i++) {
        var lhsInfo = contents.get(i - 1);
        var rhsInfo = contents.get(i);
        var lhs = lhsInfo._2;
        var rhs = rhsInfo._2;
        var lhsSubst = new Substituter.TermSubst(MutableMap.of());
        var rhsSubst = new Substituter.TermSubst(MutableMap.of());
        var ctx = PatUnify.unifyPat(lhs.patterns(), rhs.patterns(), lhsSubst, rhsSubst);
        var lhsTerm = lhs.body().subst(lhsSubst);
        var rhsTerm = rhs.body().subst(rhsSubst);
        var unification = tycker.unifier(pos, Ordering.Eq, ctx).compare(lhsTerm, rhsTerm, result);
        if (!unification) {
          tycker.reporter.report(new ConfluenceError(pos, lhsInfo._1 + 1, rhsInfo._1 + 1, lhsTerm, rhsTerm));
          throw new ExprTycker.TyckInterruptedException();
        }
      }
    }
  }

  /**
   * @param subPatsSeq should be of the same length, and should <strong>not</strong> be empty.
   * @param coverage   if true, in uncovered cases an error will be reported
   * @return pattern classes
   */
  private @NotNull ImmutableSeq<PatClass> classifySub(@NotNull ImmutableSeq<SubPats> subPatsSeq, boolean coverage) {
    assert subPatsSeq.isNotEmpty();
    var pivot = subPatsSeq.first();
    // Done
    if (pivot.pats.isEmpty()) {
      var oneClass = subPatsSeq.map(SubPats::ix);
      return ImmutableSeq.of(new PatClass(oneClass));
    }
    var explicit = pivot.head().explicit();
    var hasTuple = subPatsSeq.view()
      .mapIndexedNotNull((index, subPats) -> subPats.head() instanceof Pat.Tuple tuple
        ? new SubPats(tuple.pats(), index) : null)
      .toImmutableSeq();
    if (hasTuple.isNotEmpty()) {
      builder.shiftEmpty(explicit);
      return classifySub(hasTuple, coverage);
    }
    var hasMatch = subPatsSeq.view()
      .mapNotNull(subPats -> subPats.head() instanceof Pat.Ctor ctor ? ctor.type() : null)
      .toImmutableSeq();
    // Progress
    if (hasMatch.isEmpty()) {
      builder.shiftEmpty(explicit);
      builder.unshift();
      return classifySub(subPatsSeq.map(SubPats::drop), coverage);
    }
    // Here we have _some_ ctor patterns, therefore cannot be any tuple patterns.
    var buffer = Buffer.<PatClass>of();
    var dataCall = hasMatch.first();
    for (var ctor : dataCall.ref().core.body()) {
      var conTele = ctor.conTele();
      if (ctor.pats().isNotEmpty()) {
        var matchy = PatMatcher.tryBuildSubstArgs(ctor.pats(), dataCall.args());
        if (matchy == null) continue;
        conTele = Term.Param.subst(conTele, matchy);
      }
      var conTeleCapture = conTele;
      var matches = subPatsSeq.view()
        .mapIndexedNotNull((ix, subPats) -> matches(subPats, ix, conTeleCapture, ctor.ref()))
        .toImmutableSeq();
      builder.shift(new PatTree(ctor.ref().name(), explicit));
      if (matches.isEmpty()) {
        if (coverage) {
          reporter.report(new MissingCaseError(pos, builder.root()));
          throw new ExprTycker.TyckInterruptedException();
        } else {
          builder.reduce();
          builder.unshift();
          continue;
        }
      }
      var classified = classifySub(matches, coverage);
      builder.reduce();
      var classes = classified.map(pat -> pat.extract(subPatsSeq).map(SubPats::drop));
      var rest = classes.flatMap(clazz -> classifySub(clazz, coverage));
      builder.unshift();
      buffer.appendAll(rest);
    }
    return buffer.toImmutableSeq();
  }

  private static @Nullable SubPats matches(SubPats subPats, int ix, ImmutableSeq<Term.Param> conTele, Var ctorRef) {
    var head = subPats.head();
    if (head instanceof Pat.Ctor ctorPat && ctorPat.ref() == ctorRef)
      return new SubPats(ctorPat.params(), ix);
    if (head instanceof Pat.Bind)
      return new SubPats(conTele.map(p -> new Pat.Bind(p.explicit(), p.ref(), p.type())), ix);
    return null;
  }

  /**
   * @author ice1000
   */
  public static record PatClass(@NotNull ImmutableSeq<Integer> contents) {
    private @NotNull ImmutableSeq<SubPats> extract(@NotNull ImmutableSeq<SubPats> subPatsSeq) {
      return contents.map(subPatsSeq::get);
    }
  }

  record SubPats(
    @NotNull SeqLike<Pat> pats,
    int ix
  ) {
    @Contract(pure = true) public @NotNull Pat head() {
      return pats.first();
    }

    @Contract(pure = true) public @NotNull SubPats drop() {
      return new SubPats(pats.view().drop(1), ix);
    }
  }
}
