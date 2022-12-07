// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.primitive.IntObjTuple2;
import kala.value.MutableValue;
import org.aya.concrete.Pattern;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Formula;
import org.aya.ref.AnyVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.tyck.Tycker;
import org.aya.tyck.error.TyckOrderError;
import org.aya.util.Arg;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.MCT;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.function.BiFunction;
import java.util.stream.Collectors;

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
    @NotNull SourcePos pos
  ) {
    return classify(clauses, telescope, tycker.state, tycker.reporter, pos);
  }

  public record PatErr(@NotNull ImmutableSeq<Arg<Pattern>> missing) {}

  @VisibleForTesting public static @NotNull MCT<Term, PatErr> classify(
    @NotNull SeqLike<? extends Pat.@NotNull Preclause<?>> clauses,
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull TyckState state,
    @NotNull Reporter reporter, @NotNull SourcePos pos
  ) {
    var classifier = new PatClassifier(reporter, pos, state, new PatTree.Builder());
    var classification = classifier.classifySub(telescope.view(), clauses.view()
      .mapIndexed((index, clause) -> new MCT.SubPats<>(clause.patterns().view(), index))
      .toImmutableSeq(), 5);
    var errRef = MutableValue.<MCT.Error<Term, PatErr>>create();
    classification.forEach(pats -> {
      if (errRef.get() == null && pats instanceof MCT.Error<Term, PatErr> error) {
        reporter.report(new ClausesProblem.MissingCase(pos, error.errorMessage()));
        errRef.set(error);
      }
    });
    // Return empty case tree on error
    return errRef.get() != null ? errRef.get() : classification;
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
        // TODO: Currently all holes at this point are in an ErrorTerm
        if (lhsTerm instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
          hole.ref().conditions.append(Tuple.of(lhsSubst, rhsTerm));
        } else if (rhsTerm instanceof ErrorTerm error && error.description() instanceof MetaTerm hole) {
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

  private static void domination(Subst rhsSubst, Reporter reporter, int lhsIx, int rhsIx, Term.Matching matching) {
    if (rhsSubst.isEmpty())
      reporter.report(new ClausesProblem.Domination(
        lhsIx + 1, rhsIx + 1, matching.sourcePos()));
  }

  private @NotNull MCT<Term, PatErr> classifySub(
    @NotNull SeqView<Term.Param> telescope,
    @NotNull ImmutableSeq<MCT.SubPats<Pat>> clauses,
    int fuel
  ) {
    return MCT.classify(telescope, clauses, (params, subPats) ->
      classifySubImpl(params, subPats, fuel));
  }

  private static @NotNull Pat head(@NotNull MCT.SubPats<Pat> subPats) {
    var head = subPats.head();
    // This 'inline' is actually a 'dereference'
    return head.inline(null);
  }

  /**
   * @param telescope must be nonempty
   * @see MCT#classify(SeqView, ImmutableSeq, BiFunction)
   */
  private @Nullable MCT<Term, PatErr> classifySubImpl(
    @NotNull SeqView<Term.Param> telescope,
    @NotNull ImmutableSeq<MCT.SubPats<Pat>> clauses, int fuel
  ) {
    // We're going to split on this type
    var target = telescope.first();
    var explicit = target.explicit();
    var normalize = target.type().normalize(state, NormalizeMode.WHNF);
    switch (normalize) {
      default -> {
        if (clauses.isEmpty())
          reporter.report(new ClausesProblem.MissingBindCase(pos, target, normalize));
      }
      // The type is sigma type, and do we have any non-catchall patterns?
      // Note that we cannot have ill-typed patterns such as constructor patterns,
      // since patterns here are already well-typed
      case SigmaTerm sigma -> {
        var hasTuple = clauses
          .mapIndexedNotNull((index, subPats) -> head(subPats) instanceof Pat.Tuple tuple
            ? new MCT.SubPats<>(tuple.pats().view(), index) : null);
        // In case we do,
        if (hasTuple.isNotEmpty()) {
          // Add a catchall pattern to the pattern tree builder since tuple patterns are irrefutable
          builder.shiftEmpty(explicit);
          // We will subst the telescope with this fake tuple term
          var thatTuple = new TupTerm(sigma.params().map(Term.Param::toTerm));
          // Do it!! Just do it!!
          var newTele = telescope.drop(1)
            .map(param -> param.subst(target.ref(), thatTuple))
            .toImmutableSeq().view();
          // Classify according to the tuple elements
          var fuelCopy = fuel;
          return classifySub(sigma.params().view(), hasTuple, fuel).flatMap(pat -> pat.propagate(
            // Then, classify according to the rest of the patterns (that comes after the tuple pattern)
            classifySub(newTele, MCT.extract(pat, clauses).map(MCT.SubPats<Pat>::drop), fuelCopy)));
        }
      }
      // THE BIG GAME
      case DataCall dataCall -> {
        // If there are no remaining clauses, probably it's due to a previous `impossible` clause,
        // but since we're going to remove this keyword, this check may not be needed in the future? LOL
        if (clauses.anyMatch(subPats -> subPats.pats().isNotEmpty()) &&
          // there are no clauses starting with a constructor pattern -- we don't need a split!
          clauses.noneMatch(subPats -> head(subPats) instanceof Pat.Ctor || head(subPats) instanceof Pat.ShapedInt)
        ) break;
        var buffer = MutableList.<MCT<Term, PatErr>>create();
        var data = dataCall.ref();
        var body = Def.dataBody(data);
        if (data.core == null) reporter.report(new TyckOrderError.NotYetTyckedError(pos, data));
        // For all constructors,
        for (var ctor : body) {
          var conTele = ctor.selfTele.view();
          // Check if this constructor is available by doing the obvious thing
          var matchy = PatTycker.mischa(dataCall, ctor, state);
          // If not, check the reason why: it may fail negatively or positively
          if (matchy.isErr()) {
            // Index unification fails negatively
            if (matchy.getErr()) {
              // If clauses is empty, we continue splitting to see
              // if we can ensure that the other cases are impossible, it would be fine.
              if (clauses.isNotEmpty() &&
                // If clauses has catch-all pattern(s), it would also be fine.
                clauses.noneMatch(seq -> head(seq) instanceof Pat.Bind)) {
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
          var matches = clauses.mapIndexedNotNull((ix, subPats) ->
            matches(subPats, ix, conTele2, ctor.ref()));
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
            buffer.append(new MCT.Error<>(ImmutableSeq.empty(),
              new PatErr(builder.root().view().map(PatTree::toPattern).toImmutableSeq())));
            builder.reduce();
            builder.unshift();
            continue;
          }
          MCT<Term, PatErr> classified;
          // The base case of classifying literals together with other patterns:
          // variable `nonEmpty` only has two kinds of patterns: bind and literal.
          // We should put all bind patterns altogether and check overlapping of literals, which avoids
          // converting them to constructor forms and preventing possible stack overflow
          // (because literal overlapping check is simple).
          var nonEmpty = matches.filter(subPats -> subPats.pats().isNotEmpty());
          var hasLit = nonEmpty.filter(subPats -> head(subPats) instanceof Pat.ShapedInt);
          var hasBind = nonEmpty.filter(subPats -> head(subPats) instanceof Pat.Bind);
          if (hasLit.isNotEmpty() && hasBind.isNotEmpty() && hasLit.size() + hasBind.size() == nonEmpty.size()) {
            // We are in the base case -- group literals by their values, and add all bind patterns to each group.
            var lits = hasLit
              .collect(Collectors.groupingBy(subPats -> ((Pat.ShapedInt) head(subPats)).repr()))
              .values().stream()
              .map(ImmutableSeq::from)
              .map(subPats -> subPats.concat(hasBind))
              .collect(ImmutableSeq.factory());
            int fuelCopy = fuel;
            var allSub = lits.map(
              // Any remaining pattern?
              subPats -> subPats.allMatch(pat -> pat.pats().sizeEquals(1))
                // No, we're done!
                ? new MCT.Leaf<Term, PatErr>(subPats.map(MCT.SubPats::ix))
                // Yes, classify the rest of them
                : classifySub(conTele2.view(), subPats, fuelCopy));
            // Always add bind patterns as a separate group. See: https://github.com/aya-prover/aya-dev/issues/437
            // even though we will report duplicated domination warnings!
            var allBinds = new MCT.Leaf<Term, PatErr>(hasBind.map(MCT.SubPats::ix));
            classified = new MCT.Node<>(dataCall, allSub.appended(allBinds));
          } else {
            classified = classifySub(conTele2.view(), matches, fuel);
          }
          builder.reduce();
          var conCall = new ConCall(dataCall.conHead(ctor.ref), conTele2.map(Term.Param::toArg));
          var newTele = telescope.drop(1)
            .map(param -> param.subst(target.ref(), conCall))
            .toImmutableSeq().view();
          var fuelCopy = fuel;
          var rest = classified.flatMap(pat -> pat.propagate(
            classifySub(newTele, MCT.extract(pat, clauses).map(MCT.SubPats::drop), fuelCopy)));
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

  private static @Nullable MCT.SubPats<Pat> matches(MCT.SubPats<Pat> subPats, int ix, FormulaTerm end) {
    var head = head(subPats);
    return head instanceof Pat.End headEnd
      && end.asFormula() instanceof Formula.Lit<Term> endF
      && headEnd.isOne() == endF.isOne() ? new MCT.SubPats<>(subPats.pats(), ix) : null;
  }

  private static @Nullable MCT.SubPats<Pat> matches(MCT.SubPats<Pat> subPats, int ix, ImmutableSeq<Term.Param> conTele, AnyVar ctorRef) {
    var head = head(subPats);
    // Literals are matched against constructor patterns
    if (head instanceof Pat.ShapedInt lit) head = lit.constructorForm();
    if (head instanceof Pat.Ctor ctorPat && ctorPat.ref() == ctorRef)
      return new MCT.SubPats<>(ctorPat.params().view(), ix);
    if (head instanceof Pat.Bind)
      return new MCT.SubPats<>(conTele.view().map(Term.Param::toPat), ix);
    return null;
  }
}
