// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.primitive.IntObjTuple2;
import kala.value.Ref;
import org.aya.concrete.Pattern;
import org.aya.core.Matching;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.generic.util.NormalizeMode;
import org.aya.ref.Var;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.Tycker;
import org.aya.tyck.error.NotYetTyckedError;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.MCT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * @author ice1000, kiva
 */
public record PatClassifier(
  @NotNull Reporter reporter,
  @NotNull SourcePos pos,
  @NotNull TyckState state,
  @NotNull PatTree.Builder builder
) {
  public static @NotNull MCT<Term, PatErr> classify(
    @NotNull SeqLike<? extends Pat.@NotNull Preclause<?>> clauses,
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull Tycker tycker,
    @NotNull SourcePos pos, boolean coverage
  ) {
    return classify(clauses, telescope, tycker.state, tycker.reporter, pos, coverage);
  }

  public record PatErr(@NotNull ImmutableSeq<Pattern> missing) {}

  public static @NotNull MCT<Term, PatErr> classify(
    @NotNull SeqLike<? extends Pat.@NotNull Preclause<?>> clauses,
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull TyckState state,
    @NotNull Reporter reporter, @NotNull SourcePos pos,
    boolean coverage
  ) {
    var classifier = new PatClassifier(reporter, pos, state, new PatTree.Builder());
    var classification = classifier.classifySub(telescope.view(), clauses.view()
      .mapIndexed((index, clause) -> new MCT.SubPats<>(clause.patterns().view(), index))
      .toImmutableSeq(), coverage, 5);
    var errRef = new Ref<MCT.Error<Term, PatErr>>();
    classification.forEach(pats -> {
      if (errRef.value == null && pats instanceof MCT.Error<Term, PatErr> error) {
        reporter.report(new ClausesProblem.MissingCase(pos, error.errorMessage()));
        errRef.value = error;
      }
    });
    // Return empty case tree on error
    return errRef.value != null ? errRef.value : classification;
  }

  public static int[] firstMatchDomination(
    @NotNull ImmutableSeq<Pattern.Clause> clauses,
    @NotNull Reporter reporter, @NotNull MCT<Term, PatErr> mct
  ) {
    if (mct instanceof MCT.Error<Term, PatErr>) return new int[0];
    // StackOverflow says they're initialized to zero
    var numbers = new int[clauses.size()];
    mct.forEach(results -> numbers[results.contents().min()]++);
    // ^ The minimum is supposed to be the first one, but why not be robust?
    for (int i = 0; i < numbers.length; i++)
      if (0 == numbers[i]) reporter.report(
        new ClausesProblem.FMDomination(i + 1, clauses.get(i).sourcePos));
    return numbers;
  }

  public static void confluence(
    @NotNull PatTycker.PatResult clauses,
    @NotNull ExprTycker tycker, @NotNull SourcePos pos,
    @NotNull MCT<Term, PatErr> mct
  ) {
    var result = clauses.result();
    mct.forEach(results -> {
      var contents = results.contents()
        .flatMap(i -> Pat.Preclause.lift(clauses.clauses().get(i))
          .map(matching -> IntObjTuple2.of(i, matching)));
      for (int i = 1, size = contents.size(); i < size; i++) {
        var lhsInfo = contents.get(i - 1);
        var rhsInfo = contents.get(i);
        var lhsSubst = new Subst(MutableMap.create());
        var rhsSubst = new Subst(MutableMap.create());
        var ctx = PatUnify.unifyPat(lhsInfo._2.patterns(), rhsInfo._2.patterns(),
          lhsSubst, rhsSubst, tycker.localCtx.deriveMap());
        domination(rhsSubst, tycker.reporter, lhsInfo._1, rhsInfo._1, rhsInfo._2);
        domination(lhsSubst, tycker.reporter, rhsInfo._1, lhsInfo._1, lhsInfo._2);
        var lhsTerm = lhsInfo._2.body().subst(lhsSubst);
        var rhsTerm = rhsInfo._2.body().subst(rhsSubst);
        // TODO: Currently all holes at this point is in an ErrorTerm
        if (lhsTerm instanceof ErrorTerm error && error.description() instanceof CallTerm.Hole hole) {
          hole.ref().conditions.append(Tuple.of(lhsSubst, rhsTerm));
        } else if (rhsTerm instanceof ErrorTerm error && error.description() instanceof CallTerm.Hole hole) {
          hole.ref().conditions.append(Tuple.of(rhsSubst, lhsTerm));
        }
        var unification = tycker.unifier(pos, Ordering.Eq, ctx).compare(lhsTerm, rhsTerm, result);
        if (!unification) {
          tycker.reporter.report(new ClausesProblem.Confluence(pos, lhsInfo._1 + 1, rhsInfo._1 + 1,
            lhsTerm, rhsTerm, lhsInfo._2.sourcePos(), rhsInfo._2.sourcePos()));
        }
      }
    });
  }

  private static void domination(Subst rhsSubst, Reporter reporter, int lhsIx, int rhsIx, Matching matching) {
    if (rhsSubst.isEmpty())
      reporter.report(new ClausesProblem.Domination(
        lhsIx + 1, rhsIx + 1, matching.sourcePos()));
  }

  private @NotNull MCT<Term, PatErr> classifySub(
    @NotNull SeqView<Term.Param> telescope,
    @NotNull ImmutableSeq<MCT.SubPats<Pat>> subPatsSeq,
    boolean coverage, int fuel
  ) {
    return MCT.classify(telescope, subPatsSeq, (params, subPats) ->
      classifySubImpl(params, subPats, coverage, fuel));
  }

  private static @NotNull Pat head(@NotNull MCT.SubPats<Pat> subPats) {
    // This 'inline' is actually a 'dereference'
    return subPats.head().inline();
  }

  /**
   * @param telescope must be nonempty
   * @param coverage  if true, in uncovered cases an error will be reported
   * @see MCT#classify(SeqView, ImmutableSeq, BiFunction)
   */
  private @Nullable MCT<Term, PatErr> classifySubImpl(
    @NotNull SeqView<Term.Param> telescope,
    @NotNull ImmutableSeq<MCT.SubPats<Pat>> subPatsSeq,
    boolean coverage, int fuel
  ) {
    // We're gonna split on this type
    var target = telescope.first();
    var explicit = target.explicit();
    var normalize = target.type().normalize(state, NormalizeMode.WHNF);
    switch (normalize) {
      default -> {
        if (subPatsSeq.isEmpty() && coverage)
          reporter.report(new ClausesProblem.MissingBindCase(pos, target, normalize));
      }
      // The type is sigma type, and do we have any non-catchall patterns?
      // Note that we cannot have ill-typed patterns such as constructor patterns,
      // since patterns here are already well-typed
      case FormTerm.Sigma sigma -> {
        var hasTuple = subPatsSeq
          .mapIndexedNotNull((index, subPats) -> head(subPats) instanceof Pat.Tuple tuple
            ? new MCT.SubPats<>(tuple.pats().view(), index) : null);
        // In case we do,
        if (hasTuple.isNotEmpty()) {
          // Add a catchall pattern to the pattern tree builder since tuple patterns are irrefutable
          builder.shiftEmpty(explicit);
          // We will subst the telescope with this fake tuple term
          var thatTuple = new IntroTerm.Tuple(sigma.params().map(Term.Param::toTerm));
          // Do it!! Just do it!!
          var newTele = telescope.drop(1)
            .map(param -> param.subst(target.ref(), thatTuple))
            .toImmutableSeq().view();
          // Classify according to the tuple elements
          var fuelCopy = fuel;
          return classifySub(sigma.params().view(), hasTuple, coverage, fuel).flatMap(pat -> pat.propagate(
            // Then, classify according to the rest of the patterns (that comes after the tuple pattern)
            classifySub(newTele, MCT.extract(pat, subPatsSeq).map(MCT.SubPats<Pat>::drop), coverage, fuelCopy)));
        }
      }
      // Only `I` might be split, we just assume that
      case CallTerm.Prim primCall -> {
        assert primCall.ref().core.id == PrimDef.ID.INTERVAL;
        // Any prim patterns?
        var lrSplit = subPatsSeq
          .mapNotNull(subPats -> head(subPats) instanceof Pat.Prim prim ? prim : null)
          .firstOption();
        if (lrSplit.isDefined()) {
          var buffer = MutableList.<MCT<Term, PatErr>>create();
          // Interval pattern matching is only available in conditions,
          // so in case we need coverage, report an error on this pattern matching
          if (coverage) reporter.report(new ClausesProblem.SplitInterval(pos, lrSplit.get()));
          // For `left` and `right`,
          for (var primName : PrimDef.Factory.LEFT_RIGHT) {
            builder.append(new PatTree(primName.id, explicit, 0));
            var prim = PrimDef.Factory.INSTANCE.getOption(primName);
            var patClass = new MCT.Leaf<>(subPatsSeq.view()
              // Filter out all patterns that matches it,
              .mapIndexedNotNull((ix, subPats) -> matches(subPats, ix, prim)).map(MCT.SubPats::ix).toImmutableSeq());
            // We extract the corresponding clauses and drop the current pattern
            var classes = MCT.extract(patClass, subPatsSeq).map(MCT.SubPats::drop);
            // Probably nonempty, and in this case, prim is defined, so we can safely call `.get`
            if (classes.isNotEmpty()) {
              // We're gonna instantiate the telescope with this term!
              var lrCall = new CallTerm.Prim(prim.get().ref, 0, ImmutableSeq.empty());
              var newTele = telescope.drop(1)
                .map(param -> param.subst(target.ref(), lrCall))
                .toImmutableSeq().view();
              // Classify according the rest of the patterns
              var rest = classifySub(newTele, classes, false, fuel);
              // We have some new classes!
              buffer.append(rest);
            }
            builder.unshift();
          }
          return new MCT.Node<>(primCall, buffer.toImmutableSeq());
        }
      }
      // THE BIG GAME
      case CallTerm.Data dataCall -> {
        // If there are no remaining clauses, probably it's due to a previous `impossible` clause,
        // but since we're gonna remove this keyword, this check may not be needed in the future? LOL
        if (subPatsSeq.anyMatch(subPats -> subPats.pats().isNotEmpty()) &&
          // there are no clauses starting with a constructor pattern -- we don't need a split!
          subPatsSeq.noneMatch(subPats -> head(subPats) instanceof Pat.Ctor)
        ) break;
        var buffer = MutableList.<MCT<Term, PatErr>>create();
        var data = dataCall.ref();
        var body = Def.dataBody(data);
        if (coverage && data.core == null) reporter.report(new NotYetTyckedError(pos, data));
        // For all constructors,
        for (var ctor : body) {
          var conTele = ctor.selfTele.view();
          // Check if this constructor is available by doing the obvious thing
          var matchy = PatTycker.mischa(dataCall, ctor, null, state);
          // If not, check the reason why: it may fail negatively or positively
          if (matchy.isErr()) {
            // Index unification fails negatively
            if (matchy.getErr()) {
              // If subPatsSeq is empty, we continue splitting to see
              // if we can ensure that the other cases are impossible, it would be fine.
              if (subPatsSeq.isNotEmpty() &&
                // If subPatsSeq has catch-all pattern(s), it would also be fine.
                subPatsSeq.noneMatch(seq -> head(seq) instanceof Pat.Bind)) {
                reporter.report(new ClausesProblem.UnsureCase(pos, ctor, dataCall));
                continue;
              }
            } else continue;
            // ^ If fails positively, this would be an impossible case
          } else conTele = conTele.map(param -> param.subst(matchy.get()));
          // Java wants a final local variable, let's alias it
          var conTele2 = conTele.toImmutableSeq();
          // Find all patterns that are either catchall or splitting on this constructor,
          // e.g. for `suc`, `suc (suc a)` will be picked
          var matches = subPatsSeq
            .mapIndexedNotNull((ix, subPats) -> matches(subPats, ix, conTele2, ctor.ref()));
          // Push this constructor to the error message builder
          builder.shift(new PatTree(ctor.ref().name(), explicit, conTele2.count(Term.Param::explicit)));
          // In case no pattern matches this constructor,
          var matchesEmpty = matches.isEmpty();
          // we consume one unit of fuel and,
          if (matchesEmpty) fuel--;
          // if the pattern has no arguments and no clause matches,
          var definitely = matchesEmpty && conTele2.isEmpty() && telescope.sizeEquals(1);
          // we report an error.
          // If we're running out of fuel, we also report an error.
          if (definitely || fuel <= 0) {
            // for non-coverage case, we don't bother
            if (coverage) buffer.append(new MCT.Error<>(ImmutableSeq.empty(),
              new PatErr(builder.root().view().map(PatTree::toPattern).toImmutableSeq())));
            builder.reduce();
            builder.unshift();
            continue;
          }
          var classified = classifySub(conTele2.view(), matches, coverage, fuel);
          builder.reduce();
          var conCall = new CallTerm.Con(dataCall.conHead(ctor.ref), conTele2.map(Term.Param::toArg));
          var newTele = telescope.drop(1)
            .map(param -> param.subst(target.ref(), conCall))
            .toImmutableSeq().view();
          var fuelCopy = fuel;
          var rest = classified.flatMap(pat -> pat.propagate(
            classifySub(newTele, MCT.extract(pat, subPatsSeq).map(MCT.SubPats<Pat>::drop), coverage, fuelCopy)));
          builder.unshift();
          buffer.append(rest);
        }
        return new MCT.Node<>(dataCall, buffer.toImmutableSeq());
      }
    }
    // Progress without pattern matching
    builder.shiftEmpty(explicit);
    builder.unshift();
    return null; // Proceed loop
  }

  private static @Nullable MCT.SubPats<Pat> matches(MCT.SubPats<Pat> subPats, int ix, Option<PrimDef> existedPrim) {
    var head = head(subPats);
    return head instanceof Pat.Prim prim && existedPrim.isNotEmpty() && prim.ref() == existedPrim.get().ref()
      || head instanceof Pat.Bind ? new MCT.SubPats<>(subPats.pats(), ix) : null;
  }

  private static @Nullable MCT.SubPats<Pat> matches(MCT.SubPats<Pat> subPats, int ix, ImmutableSeq<Term.Param> conTele, Var ctorRef) {
    var head = head(subPats);
    if (head instanceof Pat.Ctor ctorPat && ctorPat.ref() == ctorRef)
      return new MCT.SubPats<>(ctorPat.params().view(), ix);
    if (head instanceof Pat.Bind)
      return new MCT.SubPats<>(conTele.view().map(Term.Param::toPat), ix);
    return null;
  }
}
