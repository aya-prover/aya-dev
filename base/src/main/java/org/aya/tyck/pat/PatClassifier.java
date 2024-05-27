// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.MutableArrayList;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import org.aya.normalize.PatMatcher;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.tyck.TyckState;
import org.aya.tyck.error.ClausesProblem;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.pat.ClassifierUtil;
import org.aya.util.tyck.pat.Indexed;
import org.aya.util.tyck.pat.PatClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.stream.Collectors;

public record PatClassifier(
  @NotNull AbstractTycker delegate, @NotNull SourcePos pos
) implements ClassifierUtil<ImmutableSeq<Term>, Term, Param, Pat>, Stateful, Problematic {
  @Override public Param subst(ImmutableSeq<Term> subst, Param param) {
    return param.instTele(subst.view());
  }
  @Override public @NotNull TyckState state() { return delegate.state(); }
  @Override public @NotNull Reporter reporter() { return delegate.reporter(); }
  @Override public Pat normalize(Pat pat) { return pat.inline((_, _) -> { }); }
  @Override public ImmutableSeq<Term> add(ImmutableSeq<Term> subst, Term term) {
    return subst.appended(term);
  }

  public static @NotNull ImmutableSeq<PatClass<ImmutableSeq<Term>>> classify(
    @NotNull SeqView<? extends Pat.@NotNull Preclause<?>> clauses,
    @NotNull SeqView<Param> telescope, @NotNull AbstractTycker tycker,
    @NotNull SourcePos pos
  ) {
    var classifier = new PatClassifier(tycker, pos);
    var cl = classifier.classifyN(ImmutableSeq.empty(), telescope, clauses
      .mapIndexed((i, clause) -> new Indexed<>(clause.pats().view(), i))
      .toImmutableSeq(), 4);
    var p = cl.partition(c -> c.cls().isEmpty());
    var missing = p.component1();
    if (missing.isNotEmpty()) tycker.fail(
      new ClausesProblem.MissingCase(pos, missing));
    return p.component2();
  }

  @Override public @NotNull ImmutableSeq<PatClass<Term>> classify1(
    @NotNull ImmutableSeq<Term> subst, @NotNull Param param,
    @NotNull ImmutableSeq<Indexed<Pat>> clauses, int fuel
  ) {
    var whnfTy = whnf(param.type());
    switch (whnfTy) {
      // Note that we cannot have ill-typed patterns such as constructor patterns under sigma,
      // since patterns here are already well-typed
      case SigmaTerm(var params) -> {
        // The type is sigma type, and do we have any non-catchall patterns?
        // In case we do,
        if (clauses.anyMatch(i -> i.pat() instanceof Pat.Tuple)) {
          var namedParams = params.mapIndexed((i, p) ->
            new Param(String.valueOf(i), p, true));
          // ^ the licit shall not matter
          var matches = clauses.mapIndexedNotNull((i, subPat) -> switch (subPat.pat()) {
            case Pat.Tuple tuple -> new Indexed<>(tuple.elements().view(), i);
            case Pat.Bind _ -> new Indexed<>(namedParams.view().map(Param::toFreshPat), i);
            default -> null;
          });
          var classes = classifyN(subst, namedParams.view(), matches, fuel);
          return classes.map(args -> new PatClass<>(new TupTerm(args.term()), args.cls()));
        }
      }
      // THE BIG GAME
      case DataCall dataCall -> {
        // In case clauses are empty, we're just making sure that the type is uninhabited,
        // so proceed as if we have valid patterns
        if (clauses.isNotEmpty() &&
          // there are no clauses starting with a constructor pattern -- we don't need a split!
          clauses.noneMatch(subPat -> subPat.pat() instanceof Pat.Con || subPat.pat() instanceof Pat.ShapedInt)
        ) break;
        var body = dataCall.ref().body();

        // Special optimization for literals
        var lits = clauses.mapNotNull(cl -> cl.pat() instanceof Pat.ShapedInt i ?
          new Indexed<>(i, cl.ix()) : null);
        var binds = Indexed.indices(clauses.filter(cl -> cl.pat() instanceof Pat.Bind));
        if (clauses.isNotEmpty() && lits.size() + binds.size() == clauses.size()) {
          // There is only literals and bind patterns, no constructor patterns
          var classes = ImmutableSeq.from(lits.collect(
              Collectors.groupingBy(i -> i.pat().repr())).values())
            .map(i -> new PatClass<>(PatToTerm.visit(i.getFirst().pat()),
              Indexed.indices(Seq.wrapJava(i)).concat(binds)));
          var ml = MutableArrayList.<PatClass<Term>>create(classes.size() + 1);
          ml.appendAll(classes);
          var maxInt = lits.max(Comparator.comparing(p -> p.pat().repr())).pat();
          var onePlus = maxInt.map(x -> x + 1).toTerm();
          ml.append(new PatClass<>(onePlus, binds));
          return ml.toImmutableSeq();
        }

        var buffer = MutableList.<PatClass<Term>>create();
        var missedCon = 0;
        // For all constructors,
        for (var con : body) {
          var fuel1 = fuel;
          var conTele = conTele(clauses, dataCall, con);
          if (conTele == null) continue;
          // Find all patterns that are either catchall or splitting on this constructor,
          // e.g. for `suc`, `suc (suc a)` will be picked
          var matches = clauses.mapIndexedNotNull((ix, subPat) ->
            // Convert to constructor form
            matches(conTele, con, ix, subPat));
          var conHead = dataCall.conHead(con);
          // The only matching cases are catch-all cases, and we skip these
          if (matches.isEmpty()) {
            missedCon++;
            fuel1--;
            // In this case we give up and do not split on this constructor
            if (conTele.isEmpty() || fuel1 <= 0) {
              var err = new ErrorTerm(Doc.plain("..."), false);
              var missingCon = new ConCall(conHead, conTele.isEmpty() ? ImmutableSeq.empty() : ImmutableSeq.of(err));
              buffer.append(new PatClass<>(missingCon, ImmutableIntSeq.empty()));
              continue;
            }
          }
          var classes = classifyN(subst, conTele.view(), matches, fuel1);
          buffer.appendAll(classes.map(args ->
            new PatClass<>(new ConCall(conHead, args.term()), args.cls())));
        }
        // If we missed all constructors, we combine the cases to a catch-all case
        if (missedCon >= body.size()) {
          return ImmutableSeq.of(new PatClass<>(param.toFreshTerm(), ImmutableIntSeq.empty()));
        }
        return buffer.toImmutableSeq();
      }
      default -> { }
    }
    return ImmutableSeq.of(new PatClass<>(param.toFreshTerm(), Indexed.indices(clauses)));
  }

  private static @Nullable Indexed<SeqView<Pat>> matches(
    ImmutableSeq<Param> conTele, ConDefLike con, int ix, Indexed<Pat> subPat
  ) {
    return switch (subPat.pat() instanceof Pat.ShapedInt i ? i.constructorForm() : subPat.pat()) {
      case Pat.Con c when c.ref().equals(con) -> new Indexed<>(c.args().view(), ix);
      case Pat.Bind _ -> new Indexed<>(conTele.view().map(Param::toFreshPat), ix);
      default -> null;
    };
  }

  public static int[] firstMatchDomination(
    @NotNull ImmutableSeq<? extends SourceNode> clauses,
    @NotNull Reporter reporter, @NotNull ImmutableSeq<? extends PatClass<?>> classes
  ) {
    return ClassifierUtil.firstMatchDomination(clauses, (pos, i) -> reporter.report(
      new ClausesProblem.FMDomination(i, pos)), classes);
  }

  private @Nullable ImmutableSeq<Param>
  conTele(@NotNull ImmutableSeq<? extends Indexed<?>> clauses, DataCall dataCall, ConDefLike con) {
    // Check if this constructor is available by doing the obvious thing
    return switch (PatternTycker.checkAvail(dataCall, con, state())) {
      // If not, check the reason why: it may fail negatively or positively
      case Result.Err(var e) -> {
        // Index unification fails negatively
        if (e == PatMatcher.State.Stuck) {
          // If clauses is empty, we continue splitting to see
          // if we can ensure that the other cases are impossible, it would be fine.
          if (clauses.isNotEmpty() &&
            // If clauses has catch-all pattern(s), it would also be fine.
            clauses.noneMatch(seq -> seq.pat() instanceof Pat.Bind)
          ) {
            fail(new ClausesProblem.UnsureCase(pos, con, dataCall));
            yield null;
          }
          yield con.selfTele(ImmutableSeq.empty());
        } else yield null;
        // ^ If fails positively, this would be an impossible case
      }
      case Result.Ok(var ok) -> con.selfTele(ok);
    };
  }
}
