// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.primitive.IntObjTuple2;
import org.aya.api.error.Reporter;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Pattern;
import org.aya.core.Matching;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.*;
import org.aya.core.visitor.Substituter;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000, kiva
 */
public record PatClassifier(
  @NotNull Reporter reporter,
  @NotNull SourcePos pos,
  @NotNull TyckState state,
  @NotNull PatTree.Builder builder
) {
  public static @NotNull ImmutableSeq<PatClass> classify(
    @NotNull ImmutableSeq<Pat.@NotNull PrototypeClause> clauses,
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull TyckState state,
    @NotNull Reporter reporter, @NotNull SourcePos pos,
    boolean coverage
  ) {
    var classifier = new PatClassifier(reporter, pos, state, new PatTree.Builder());
    var classification = classifier.classifySub(telescope, clauses
      .mapIndexed((index, clause) -> new SubPats(clause.patterns().view(), index)), coverage, 5);
    for (var pats : classification) {
      if (pats instanceof PatClass.Err err) {
        reporter.report(new ClausesProblem.MissingCase(pos, err.errorMessage));
        return ImmutableSeq.empty();
      }
    }
    return classification;
  }

  public static void firstMatchDomination(
    @NotNull ImmutableSeq<Pat.@NotNull PrototypeClause> clauses,
    @NotNull Reporter reporter, @NotNull SourcePos pos,
    @NotNull ImmutableSeq<PatClass> classification
  ) {
    // Google says they're initialized to false
    var numbers = new boolean[clauses.size()];
    for (var results : classification) numbers[results.contents().min()] = true;
    // ^ The minimum is supposed to be the first one, but why not be robust?
    for (int i = 0; i < numbers.length; i++)
      if (!numbers[i]) reporter.report(new ClausesProblem.FMDomination(i + 1, pos));
  }

  public static void confluence(
    @NotNull ImmutableSeq<Pat.@NotNull PrototypeClause> clauses,
    @NotNull ExprTycker tycker, @NotNull SourcePos pos,
    @NotNull Term result, @NotNull ImmutableSeq<PatClass> classification
  ) {
    for (var results : classification) {
      var contents = results.contents()
        .flatMap(i -> Pat.PrototypeClause.deprototypify(clauses.get(i))
          .map(matching -> IntObjTuple2.of(i, matching)));
      for (int i = 1, size = contents.size(); i < size; i++) {
        var lhsInfo = contents.get(i - 1);
        var rhsInfo = contents.get(i);
        var lhsSubst = new Substituter.TermSubst(MutableMap.create());
        var rhsSubst = new Substituter.TermSubst(MutableMap.create());
        PatUnify.unifyPat(lhsInfo._2.patterns(), rhsInfo._2.patterns(), lhsSubst, rhsSubst);
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
        var unification = tycker.unifier(pos, Ordering.Eq).compare(lhsTerm, rhsTerm, result);
        if (!unification) {
          tycker.reporter.report(new ClausesProblem.Confluence(pos, lhsInfo._1 + 1, rhsInfo._1 + 1,
            lhsTerm, rhsTerm, lhsInfo._2.sourcePos(), rhsInfo._2.sourcePos()));
        }
      }
    }
  }

  private static void domination(Substituter.TermSubst rhsSubst, Reporter reporter, int lhsIx, int rhsIx, Matching matching) {
    if (rhsSubst.isEmpty())
      reporter.report(new ClausesProblem.Domination(
        lhsIx + 1, rhsIx + 1, matching.sourcePos()));
  }

  /**
   * Helper method to avoid stack being too deep and fuel being consumed for distinct patterns.
   *
   * @param subPatsSeq should be of the same length, and should <strong>not</strong> be empty.
   * @param coverage   if true, in uncovered cases an error will be reported
   * @return pattern classes
   */
  private @NotNull ImmutableSeq<PatClass> classifySub(
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull ImmutableSeq<SubPats> subPatsSeq,
    boolean coverage, int fuel
  ) {
    while (telescope.isNotEmpty()) {
      var res = classifySubImpl(telescope, subPatsSeq, coverage, fuel);
      if (res != null) return res;
      else {
        telescope = telescope.drop(1);
        subPatsSeq = subPatsSeq.map(SubPats::drop);
      }
    }
    // Done
    return ImmutableSeq.of(new PatClass.Ok(subPatsSeq.map(SubPats::ix)));
  }

  /**
   * @param telescope must be nonempty
   * @see #classifySub(ImmutableSeq, ImmutableSeq, boolean, int)
   */
  private @Nullable ImmutableSeq<PatClass> classifySubImpl(
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull ImmutableSeq<SubPats> subPatsSeq,
    boolean coverage, int fuel
  ) {
    // We're gonna split on this type
    var target = telescope.first();
    var explicit = target.explicit();
    switch (target.type().normalize(state, NormalizeMode.WHNF)) {
      default -> {
      }
      // The type is sigma type, and do we have any non-catchall patterns?
      // Note that we cannot have ill-typed patterns such as constructor patterns,
      // since patterns here are already well-typed
      case FormTerm.Sigma sigma -> {
        var hasTuple = subPatsSeq
          .mapIndexedNotNull((index, subPats) -> subPats.head() instanceof Pat.Tuple tuple
            ? new SubPats(tuple.pats().view(), index) : null);
        // In case we do,
        if (hasTuple.isNotEmpty()) {
          // Add a catchall pattern to the pattern tree builder since tuple patterns are irrefutable
          builder.shiftEmpty(explicit);
          // We will subst the telescope with this fake tuple term
          var thatTuple = new IntroTerm.Tuple(sigma.params().map(Term.Param::toTerm));
          // Do it!! Just do it!!
          var newTele = telescope.view()
            .drop(1)
            .map(param -> param.subst(target.ref(), thatTuple))
            .toImmutableSeq();
          // Classify according to the tuple elements
          var fuelCopy = fuel;
          return classifySub(sigma.params(), hasTuple, coverage, fuel)
            .flatMap(pat -> mapClass(pat,
              // Then, classify according to the rest of the patterns (that comes after the tuple pattern)
              classifySub(newTele, PatClass.extract(pat, subPatsSeq).map(SubPats::drop), coverage, fuelCopy)));
        }
      }
      // Only `I` might be split, we just assume that
      case CallTerm.Prim primCall -> {
        assert primCall.ref().core.id == PrimDef.ID.INTERVAL;
        // Any prim patterns?
        var lrSplit = subPatsSeq
          .mapNotNull(subPats -> subPats.head() instanceof Pat.Prim prim ? prim : null)
          .firstOption();
        if (lrSplit.isDefined()) {
          var buffer = DynamicSeq.<PatClass>create();
          // Interval pattern matching is only available in conditions,
          // so in case we need coverage, report an error on this pattern matching
          if (coverage) reporter.report(new ClausesProblem.SplitInterval(pos, lrSplit.get()));
          // For `left` and `right`,
          for (var primName : PrimDef.Factory.LEFT_RIGHT) {
            builder.append(new PatTree(primName.id, explicit, 0));
            var prim = PrimDef.Factory.INSTANCE.getOption(primName);
            var patClass = new PatClass.Ok(subPatsSeq.view()
              // Filter out all patterns that matches it,
              .mapIndexedNotNull((ix, subPats) -> matches(subPats, ix, prim)).map(SubPats::ix).toImmutableSeq());
            // We extract the corresponding clauses and drop the current pattern
            var classes = PatClass.extract(patClass, subPatsSeq).map(SubPats::drop);
            // Probably nonempty, and in this case, prim is defined, so we can safely call `.get`
            if (classes.isNotEmpty()) {
              // We're gonna instantiate the telescope with this term!
              var lrCall = new CallTerm.Prim(prim.get().ref, ImmutableSeq.empty(), ImmutableSeq.empty());
              var newTele = telescope.view()
                .drop(1)
                .map(param -> param.subst(target.ref(), lrCall))
                .toImmutableSeq();
              // Classify according the rest of the patterns
              var rest = classifySub(newTele, classes, false, fuel);
              // We have some new classes!
              buffer.appendAll(rest);
            }
            builder.unshift();
          }
          return buffer.toImmutableSeq();
        }
      }
      // THE BIG GAME
      case CallTerm.Data dataCall -> {
        // If there are no remaining clauses, probably it's due to a previous `impossible` clause,
        // but since we're gonna remove this keyword, this check may not be needed in the future? LOL
        if (subPatsSeq.anyMatch(subPats -> subPats.pats.isNotEmpty()) &&
          // there are no clauses starting with a constructor pattern -- we don't need a split!
          subPatsSeq.noneMatch(subPats -> subPats.head() instanceof Pat.Ctor)
        ) break;
        var buffer = DynamicSeq.<PatClass>create();
        // For all constructors,
        for (var ctor : dataCall.ref().core.body) {
          var conTele = ctor.selfTele;
          // Check if this constructor is available by doing the obvious thing
          if (ctor.pats.isNotEmpty()) {
            var matchy = PatMatcher.tryBuildSubstArgs(ctor.pats, dataCall.args());
            // If not, check the reason why: it may fail negatively or positively
            if (matchy.isErr()) {
              // Index unification fails negatively
              if (matchy.getErr()) {
                // If subPatsSeq is empty, we continue splitting to see
                // if we can ensure that the other cases are impossible, it would be fine.
                if (subPatsSeq.isNotEmpty() &&
                  // If subPatsSeq has catch-all pattern(s), it would also be fine.
                  subPatsSeq.noneMatch(seq -> seq.head() instanceof Pat.Bind)) {
                  reporter.report(new ClausesProblem.UnsureCase(pos, ctor, dataCall));
                  continue;
                }
              } else continue;
              // ^ If fails positively, this would be an impossible case
            } else conTele = conTele.map(param -> param.subst(matchy.get()));
          }
          // Java wants a final local variable, let's alias it
          var conTeleCapture = conTele;
          // Find all patterns that are either catchall or splitting on this constructor,
          // e.g. for `suc`, `suc (suc a)` will be picked
          var matches = subPatsSeq
            .mapIndexedNotNull((ix, subPats) -> matches(subPats, ix, conTeleCapture, ctor.ref()));
          // Push this constructor to the error message builder
          builder.shift(new PatTree(ctor.ref().name(), explicit, conTele.count(Term.Param::explicit)));
          // In case no pattern matches this constructor,
          var matchesEmpty = matches.isEmpty();
          // we consume one unit of fuel and,
          if (matchesEmpty) fuel--;
          // if the pattern has no arguments and no clause matches,
          var definitely = matchesEmpty && conTele.isEmpty() && telescope.sizeEquals(1);
          // we report an error.
          // If we're running out of fuel, we also report an error.
          if (definitely || fuel <= 0) {
            // for non-coverage case, we don't bother
            if (coverage) buffer.append(new PatClass.Err(ImmutableSeq.empty(),
              builder.root().view().map(PatTree::toPattern).toImmutableSeq()));
            builder.reduce();
            builder.unshift();
            continue;
          }
          var classified = classifySub(conTele, matches, coverage, fuel);
          builder.reduce();
          var conCall = new CallTerm.Con(dataCall.conHead(ctor.ref), conTeleCapture.map(Term.Param::toArg));
          var newTele = telescope.view()
            .drop(1)
            .map(param -> param.subst(target.ref(), conCall))
            .toImmutableSeq();
          var fuelCopy = fuel;
          var rest = classified.flatMap(pat ->
            mapClass(pat, classifySub(newTele, PatClass.extract(pat, subPatsSeq)
              .map(SubPats::drop), coverage, fuelCopy)));
          builder.unshift();
          buffer.appendAll(rest);
        }
        return buffer.toImmutableSeq();
      }
    }
    // Progress without pattern matching
    builder.shiftEmpty(explicit);
    builder.unshift();
    return null; // Proceed loop
  }

  private ImmutableSeq<PatClass> mapClass(@NotNull PatClass pat, @NotNull ImmutableSeq<PatClass> classes) {
    return switch (pat) {
      case PatClass.Ok ok -> classes;
      case PatClass.Err err -> classes.map(newClz -> new PatClass.Err(newClz.contents(), err.errorMessage));
    };
  }

  private static @Nullable SubPats matches(SubPats subPats, int ix, Option<PrimDef> existedPrim) {
    var head = subPats.head();
    return head instanceof Pat.Prim prim && existedPrim.isNotEmpty() && prim.ref() == existedPrim.get().ref()
      || head instanceof Pat.Bind ? new SubPats(subPats.pats, ix) : null;
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
  public sealed interface PatClass {
    @NotNull ImmutableSeq<Integer> contents();
    record Ok(@NotNull ImmutableSeq<Integer> contents) implements PatClass {
    }

    record Err(
      @NotNull ImmutableSeq<Integer> contents,
      @NotNull ImmutableSeq<Pattern> errorMessage
    ) implements PatClass {
    }

    private @NotNull static ImmutableSeq<SubPats> extract(PatClass pats, @NotNull ImmutableSeq<SubPats> subPatsSeq) {
      return pats.contents().map(subPatsSeq::get);
    }
  }

  private record SubPats(@NotNull SeqView<Pat> pats, int ix) {
    @Contract(pure = true) public @NotNull Pat head() {
      if (pats.isEmpty()) {
        throw new IllegalStateException("Empty pattern list");
      }
      return pats.first();
    }

    @Contract(pure = true) public @NotNull SubPats drop() {
      return new SubPats(pats.drop(1), ix);
    }
  }
}
