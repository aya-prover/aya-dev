// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.tuple.primitive.IntObjTuple2;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.ClausesProblem;
import org.aya.util.Ordering;
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
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Reporter reporter, @NotNull SourcePos pos,
    boolean coverage
  ) {
    var classifier = new PatClassifier(reporter, pos, new PatTree.Builder());
    return classifier.classifySub(telescope.view(), clauses
      .mapIndexed((index, clause) -> new SubPats(clause.patterns().view(), index))
      .view(), coverage);
  }

  public static void confluence(
    @NotNull ImmutableSeq<Pat.@NotNull PrototypeClause> clauses,
    @NotNull ExprTycker tycker, @NotNull SourcePos pos,
    @NotNull Term result, @NotNull ImmutableSeq<PatClass> classification
  ) {
    for (var results : classification) {
      var contents = results.contents
        .flatMap(i -> Pat.PrototypeClause.deprototypify(clauses.get(i))
          .map(matching -> IntObjTuple2.of(i, matching)))
        .toImmutableSeq();
      for (int i = 1, size = contents.size(); i < size; i++) {
        var lhsInfo = contents.get(i - 1);
        var rhsInfo = contents.get(i);
        var lhs = lhsInfo._2;
        var rhs = rhsInfo._2;
        var lhsSubst = new Substituter.TermSubst(MutableMap.create());
        var rhsSubst = new Substituter.TermSubst(MutableMap.create());
        PatUnify.unifyPat(lhs.patterns(), rhs.patterns(), lhsSubst, rhsSubst);
        var lhsTerm = lhs.body().subst(lhsSubst);
        var rhsTerm = rhs.body().subst(rhsSubst);
        var unification = tycker.unifier(pos, Ordering.Eq).compare(lhsTerm, rhsTerm, result);
        if (!unification) {
          tycker.reporter.report(new ClausesProblem.Confluence(pos, lhsInfo._1 + 1, rhsInfo._1 + 1,
            lhsTerm, rhsTerm, lhsInfo._2.sourcePos(), rhsInfo._2.sourcePos()));
        }
      }
    }
  }

  /**
   * @param subPatsSeq should be of the same length, and should <strong>not</strong> be empty.
   * @param coverage   if true, in uncovered cases an error will be reported
   * @return pattern classes
   */
  private @NotNull ImmutableSeq<PatClass> classifySub(
    @NotNull SeqView<Term.Param> telescope,
    @NotNull SeqView<SubPats> subPatsSeq,
    boolean coverage
  ) {
    // Done
    if (telescope.isEmpty()) {
      var oneClass = subPatsSeq.map(SubPats::ix);
      return ImmutableSeq.of(new PatClass(oneClass));
    }
    assert subPatsSeq.isNotEmpty();
    var target = telescope.first();
    var explicit = target.explicit();
    switch (target.type().normalize(NormalizeMode.WHNF)) {
      default -> {
      }
      case FormTerm.Sigma sigma -> {
        var hasTuple = subPatsSeq
          .mapIndexedNotNull((index, subPats) -> subPats.head() instanceof Pat.Tuple tuple
            ? new SubPats(tuple.pats().view(), index) : null)
          .toImmutableSeq();
        if (hasTuple.isNotEmpty()) {
          builder.shiftEmpty(explicit);
          // TODO[ice]: I think this is broken.
          return classifySub(sigma.params().view(), hasTuple.view(), coverage);
        }
      }
      case CallTerm.Prim primCall && primCall.ref().core.id == PrimDef.ID.INTERVAL -> {
        var lrSplit = subPatsSeq
          .mapNotNull(subPats -> subPats.head() instanceof Pat.Prim prim ? prim : null)
          .firstOption();
        var buffer = Buffer.<PatClass>create();
        var droppedTele = telescope.drop(1);
        if (lrSplit.isDefined()) {
          if (coverage) reporter.report(new ClausesProblem.SplitInterval(pos, lrSplit.get()));
          for (var primName : PrimDef.Factory.LEFT_RIGHT) {
            builder.append(new PatTree(primName.id, explicit));
            var classes = new PatClass(subPatsSeq
              .mapIndexedNotNull((ix, subPats) -> {
                var head = subPats.head();
                var existedPrim = PrimDef.Factory.INSTANCE.getOption(primName);
                return head instanceof Pat.Prim prim && existedPrim.isNotEmpty() && prim.ref() == existedPrim.get().ref()
                  || head instanceof Pat.Bind ? new SubPats(subPats.pats, ix) : null;
              }).map(SubPats::ix).toImmutableSeq().view())
              .extract(subPatsSeq)
              .map(SubPats::drop);
            if (classes.isNotEmpty()) {
              var rest = classifySub(droppedTele, classes, false);
              builder.unshift();
              buffer.appendAll(rest);
            } else builder.unshift();
          }
          return buffer.toImmutableSeq();
        }
      }
      case CallTerm.Data dataCall -> {
        if (subPatsSeq.noneMatch(subPats -> subPats.pats.isEmpty() || subPats.head() instanceof Pat.Ctor))
          break;
        var buffer = Buffer.<PatClass>create();
        var droppedTele = telescope.drop(1);
        for (var ctor : dataCall.ref().core.body) {
          var conTele = ctor.selfTele;
          if (ctor.pats.isNotEmpty()) {
            var matchy = PatMatcher.tryBuildSubstArgs(ctor.pats, dataCall.args());
            if (matchy == null) continue;
            conTele = conTele.map(param -> param.subst(matchy));
          }
          var conTeleCapture = conTele;
          var matches = subPatsSeq.toImmutableSeq()
            .mapIndexedNotNull((ix, subPats) -> matches(subPats, ix, conTeleCapture, ctor.ref()));
          builder.shift(new PatTree(ctor.ref().name(), explicit));
          if (matches.isEmpty()) {
            if (coverage) reporter.report(new ClausesProblem.MissingCase(pos, builder.root().toImmutableSeq()));
            builder.reduce();
            builder.unshift();
            continue;
          }
          var classified = classifySub(conTele.view(), matches.view(), coverage);
          builder.reduce();
          var classes = classified.map(pat -> pat
            .extract(subPatsSeq)
            .map(SubPats::drop));
          var rest = classes.flatMap(clazz -> classifySub(droppedTele, clazz, coverage));
          builder.unshift();
          buffer.appendAll(rest);
        }
        return buffer.toImmutableSeq();
      }
    }
    // Progress without pattern matching
    builder.shiftEmpty(explicit);
    builder.unshift();
    return classifySub(telescope.drop(1), subPatsSeq.map(SubPats::drop), coverage);
  }

  private static @Nullable SubPats matches(SubPats subPats, int ix, ImmutableSeq<Term.Param> conTele, Var ctorRef) {
    var head = subPats.head();
    if (head instanceof Pat.Ctor ctorPat && ctorPat.ref() == ctorRef)
      return new SubPats(ctorPat.params().view(), ix);
    if (head instanceof Pat.Bind)
      return new SubPats(conTele.view().map(p -> new Pat.Bind(p.explicit(), p.ref(), p.type())), ix);
    return null;
  }

  /**
   * @author ice1000
   */
  public static record PatClass(@NotNull SeqView<Integer> contents) {
    private @NotNull SeqView<SubPats> extract(@NotNull SeqView<SubPats> subPatsSeq) {
      return contents.map(subPatsSeq::get);
    }
  }

  record SubPats(@NotNull SeqView<Pat> pats, int ix) {
    @Contract(pure = true) public @NotNull Pat head() {
      return pats.first();
    }

    @Contract(pure = true) public @NotNull SubPats drop() {
      return new SubPats(pats.drop(1), ix);
    }
  }
}
