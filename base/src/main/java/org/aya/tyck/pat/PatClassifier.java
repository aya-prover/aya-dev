// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.primitive.IntObjTuple2;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Pattern;
import org.aya.core.def.PrimDef;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatMatcher;
import org.aya.core.pat.PatUnify;
import org.aya.core.term.CallTerm;
import org.aya.core.term.FormTerm;
import org.aya.core.term.IntroTerm;
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
    var classification = classifier.classifySub(telescope, clauses
      .mapIndexed((index, clause) -> new SubPats(clause.patterns().view(), index)), coverage);
    for (var pats : classification) {
      if (pats instanceof PatClass.Err err) {
        reporter.report(new ClausesProblem.MissingCase(pos, err.errorMessage));
        return ImmutableSeq.empty();
      }
    }
    return classification;
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
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull ImmutableSeq<SubPats> subPatsSeq,
    boolean coverage
  ) {
    // Done
    if (telescope.isEmpty()) {
      var oneClass = subPatsSeq.map(SubPats::ix);
      return ImmutableSeq.of(new PatClass.Ok(oneClass));
    }
    // We're gonna split on this type
    var target = telescope.first();
    var explicit = target.explicit();
    switch (target.type().normalize(NormalizeMode.WHNF)) {
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
          return classifySub(sigma.params(), hasTuple, coverage)
            .flatMap(pat -> mapClass(pat,
              // Then, classify according to the rest of the patterns (that comes after the tuple pattern)
              classifySub(newTele, PatClass.extract(pat, subPatsSeq).map(SubPats::drop), coverage)));
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
          var buffer = Buffer.<PatClass>create();
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
              var rest = classifySub(newTele, classes, false);
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
        var buffer = Buffer.<PatClass>create();
        // For all constructors,
        for (var ctor : dataCall.ref().core.body) {
          var conTele = ctor.selfTele;
          // Check if this constructor is available by doing the obvious thing
          if (ctor.pats.isNotEmpty()) {
            var matchy = PatMatcher.tryBuildSubstArgs(ctor.pats, dataCall.args());
            // If not, forget about this constructor
            if (matchy == null) continue;
            conTele = conTele.map(param -> param.subst(matchy));
          }
          // Java wants a final local variable, let's alias it
          var conTeleCapture = conTele;
          // Find all patterns that are either catchall or splitting on this constructor,
          // e.g. for `suc`, `suc (suc a)` will be picked
          var matches = subPatsSeq
            .mapIndexedNotNull((ix, subPats) -> matches(subPats, ix, conTeleCapture, ctor.ref()));
          // Push this constructor to the error message builder
          builder.shift(new PatTree(ctor.ref().name(), explicit, conTele.count(Term.Param::explicit)));
          if (telescope.sizeEquals(1) && matches.isEmpty()) {
            if (coverage) buffer.append(new PatClass.Err(ImmutableSeq.empty(),
              builder.root().view().map(PatTree::toPattern).toImmutableSeq()));
            builder.reduce();
            builder.unshift();
            continue;
          }
          var classified = classifySub(conTele, matches, coverage);
          builder.reduce();
          var conCall = new CallTerm.Con(dataCall.conHead(ctor.ref), conTeleCapture.map(Term.Param::toArg));
          var newTele = telescope.view()
            .drop(1)
            .map(param -> param.subst(target.ref(), conCall))
            .toImmutableSeq();
          var rest = classified.flatMap(pat ->
            mapClass(pat, classifySub(newTele, PatClass.extract(pat, subPatsSeq).map(SubPats::drop), coverage)));
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

  record SubPats(@NotNull SeqView<Pat> pats, int ix) {
    @Contract(pure = true) public @NotNull Pat head() {
      return pats.first();
    }

    @Contract(pure = true) public @NotNull SubPats drop() {
      return new SubPats(pats.drop(1), ix);
    }
  }
}
