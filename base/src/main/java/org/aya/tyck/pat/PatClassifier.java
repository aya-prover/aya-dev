// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.concrete.Pattern;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.core.visitor.EndoTerm;
import org.aya.core.visitor.Subst;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.error.TyckOrderError;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.tycker.StatedTycker;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.pat.Indexed;
import org.aya.util.tyck.pat.PatClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.stream.Collectors;

/**
 * Formerly known as <code>PatClassifier</code>.
 *
 * @author ice1000
 */
public final class PatClassifier extends StatedTycker {
  public final @NotNull SourcePos pos;

  public PatClassifier(
    @NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder,
    @NotNull TyckState state, @NotNull SourcePos pos
  ) {
    super(reporter, traceBuilder, state);
    this.pos = pos;
  }

  public static @NotNull ImmutableSeq<PatClass<ImmutableSeq<Arg<Term>>>> classify(
    @NotNull SeqLike<? extends Pat.@NotNull Preclause<?>> clauses,
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull StatedTycker tycker,
    @NotNull SourcePos pos
  ) {
    return classify(clauses, telescope, tycker.state, tycker.reporter, pos, tycker.traceBuilder);
  }

  /**
   * Don't change the return type, it interferes with type inference.
   */
  private static Option<Term> err(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.view().mapNotNull(arg ->
      arg.term() instanceof ErrorTerm ? arg.term() : null).firstOption();
  }

  @VisibleForTesting public static @NotNull ImmutableSeq<PatClass<ImmutableSeq<Arg<Term>>>>
  classify(
    @NotNull SeqLike<? extends Pat.@NotNull Preclause<?>> clauses,
    @NotNull ImmutableSeq<Term.Param> telescope, @NotNull TyckState state,
    @NotNull Reporter reporter, @NotNull SourcePos pos, Trace.@Nullable Builder builder
  ) {
    var classifier = new PatClassifier(reporter, builder, state, pos);
    var cl = classifier.classifyN(false, new Subst(), telescope.view(), clauses.view()
      .mapIndexed((i, clause) -> new Indexed<>(clause.patterns().view().map(Arg::term), i))
      .toImmutableSeq(), 5);
    return cl.filter(it -> {
      if (it.cls().isEmpty()) {
        reporter.report(new ClausesProblem.MissingCase(pos, it.term()));
        return false;
      }
      return true;
    });
  }

  public @NotNull ImmutableSeq<PatClass<ImmutableSeq<Arg<Term>>>> classifyN(
    boolean hasCatchAll, @NotNull Subst subst, @NotNull SeqView<Term.Param> params,
    @NotNull ImmutableSeq<Indexed<SeqView<Pat>>> clauses, int fuel
  ) {
    if (params.isEmpty()) return ImmutableSeq.of(new PatClass<>(
      ImmutableSeq.empty(), Indexed.indices(clauses)));
    var first = params.first();
    var cls = classify1(hasCatchAll, subst, first.subst(subst),
      clauses.mapIndexed((ix, it) -> new Indexed<>(it.pat().first().inline(null), ix)), fuel);
    return cls.flatMap(subclauses ->
      classifyN(hasCatchAll, subst.add(first.ref(), subclauses.term().term()),
        // Drop heads of both
        params.drop(1),
        subclauses.extract(clauses.map(it ->
          new Indexed<>(it.pat().drop(1), it.ix()))), fuel)
        .map(args -> args.map(ls -> ls.prepended(subclauses.term()))));
  }

  /**
   * @return Possibilities
   */
  @NotNull ImmutableSeq<PatClass<Arg<Term>>> classify1(
    boolean preHasCatchAll, @NotNull Subst subst, @NotNull Term.Param param,
    @NotNull ImmutableSeq<Indexed<Pat>> clauses, int fuel
  ) {
    var whnfTy = whnf(param.type());
    var clsWithBindPat = clauses.view().mapIndexedNotNull((i, subPat) ->
        subPat.pat() instanceof Pat.Bind ? i : null)
      .collect(ImmutableIntSeq.factory());
    var hasCatchAll = preHasCatchAll || clsWithBindPat.isNotEmpty();
    final var explicit = param.explicit();
    switch (whnfTy) {
      default -> {
      }
      // Note that we cannot have ill-typed patterns such as constructor patterns,
      // since patterns here are already well-typed
      case SigmaTerm(var params) -> {
        // The type is sigma type, and do we have any non-catchall patterns?
        var clsWithTupPat = clauses.mapIndexedNotNull((i, subPat) ->
          subPat.pat() instanceof Pat.Tuple tuple
            ? new Indexed<>(tuple.pats().view().map(Arg::term), i)
            : null);
        // In case we do,
        if (clsWithTupPat.isNotEmpty()) {
          params = new EndoTerm.Renamer().params(params.view());
          var classes = classifyN(hasCatchAll, subst.derive(), params.view(), clsWithTupPat, fuel);
          return classes.map(args -> new PatClass<>(
            new Arg<>(err(args.term()).getOrElse(() -> new TupTerm(args.term())), explicit),
            args.cls().appendedAll(clsWithBindPat)));
        }
      }
      case DataCall dataCall -> {
        // In case clauses are empty, we're just making sure that the type is uninhabited,
        // so proceed as if we have valid patterns
        if (clauses.isNotEmpty() &&
          // there are no clauses starting with a constructor pattern -- we don't need a split!
          clauses.noneMatch(subPat -> subPat.pat() instanceof Pat.Ctor || subPat.pat() instanceof Pat.ShapedInt)
        ) break;
        var buffer = MutableList.<PatClass<Arg<Term>>>create();
        var data = dataCall.ref();
        var body = Def.dataBody(data);
        if (data.core == null) reporter.report(new TyckOrderError.NotYetTyckedError(pos, data));
        // For all constructors,
        for (var ctor : body) {
          var fuel1 = fuel;
          var conTeleView = conTele(clauses, dataCall, ctor, pos);
          if (conTeleView == null) continue;
          var conTele = new EndoTerm.Renamer().params(conTeleView);
          // Find all patterns that are either catchall or splitting on this constructor,
          // e.g. for `suc`, `suc (suc a)` will be picked
          var matches = clauses.mapIndexedNotNull((ix, subPat) ->
            // Convert to constructor form
            (subPat.pat() instanceof Pat.ShapedInt i
              ? i.constructorForm()
              : subPat.pat()
              // Then turn into `SubPats`
            ) instanceof Pat.Ctor c && c.ref() == ctor.ref()
              ? new Indexed<>(c.params().view().map(Arg::term), ix)
              : null);
          var conHead = dataCall.conHead(ctor.ref);
          // The only matching cases are catch-all cases, and we skip these
          if (matches.isEmpty()) {
            if (hasCatchAll) {
              buffer.append(new PatClass<>(new Arg<>(new RefTerm(param.ref()), explicit), clsWithBindPat));
              continue;
            }
            fuel1--;
            // In this case we give up and do not split on this constructor
            if (conTele.isEmpty() || fuel1 <= 0) {
              var err = new ErrorTerm(Doc.plain("..."), false);
              buffer.append(new PatClass<>(new Arg<>(new ConCall(conHead,
                conTele.isEmpty() ? ImmutableSeq.empty() : ImmutableSeq.of(new Arg<>(err, true))),
                explicit), ImmutableIntSeq.empty()));
              continue;
            }
          }
          ImmutableSeq<PatClass<ImmutableSeq<Arg<Term>>>> classes;
          var lits = clauses.mapNotNull(cl -> cl.pat() instanceof Pat.ShapedInt i ?
            new Indexed<>(i, cl.ix()) : null);
          if (clauses.isNotEmpty() && lits.size() + clsWithBindPat.size() == clauses.size()) {
            // There is only literals and bind patterns, no constructor patterns
            classes = ImmutableSeq.from(lits.collect(Collectors.groupingBy(i -> i.pat().repr())).values())
              .map(i -> new PatClass<>(ImmutableSeq.of(new Arg<>(i.get(0).pat().toTerm(), explicit)),
                Indexed.indices(Seq.wrapJava(i))));
          } else {
            classes = classifyN(hasCatchAll, subst.derive(), conTele.view(), matches, fuel1);
          }
          buffer.appendAll(classes.map(args -> new PatClass<>(
            new Arg<>(err(args.term()).getOrElse(() -> new ConCall(conHead, args.term())), explicit),
            args.cls().appendedAll(clsWithBindPat))));
        }
        return buffer.toImmutableSeq();
      }
    }
    return ImmutableSeq.of(new PatClass<>(param.toArg(), Indexed.indices(clauses)));
  }

  public static int[] firstMatchDomination(
    @NotNull ImmutableSeq<Pattern.Clause> clauses,
    @NotNull Reporter reporter, @NotNull ImmutableSeq<? extends PatClass<?>> classes
  ) {
    // StackOverflow says they're initialized to zero
    var numbers = new int[clauses.size()];
    classes.forEach(results ->
      numbers[results.cls().min()]++);
    // ^ The minimum is not always the first one
    for (int i = 0; i < numbers.length; i++)
      if (0 == numbers[i]) reporter.report(
        new ClausesProblem.FMDomination(i + 1, clauses.get(i).sourcePos));
    return numbers;
  }

  private @Nullable SeqView<Term.Param>
  conTele(@NotNull ImmutableSeq<? extends Indexed<?>> clauses, DataCall dataCall, CtorDef ctor, @NotNull SourcePos pos) {
    var conTele = ctor.selfTele.view();
    // Check if this constructor is available by doing the obvious thing
    var matchy = PatternTycker.mischa(dataCall, ctor, state);
    // If not, check the reason why: it may fail negatively or positively
    if (matchy.isErr()) {
      // Index unification fails negatively
      if (matchy.getErr()) {
        // If clauses is empty, we continue splitting to see
        // if we can ensure that the other cases are impossible, it would be fine.
        if (clauses.isNotEmpty() &&
          // If clauses has catch-all pattern(s), it would also be fine.
          clauses.noneMatch(seq -> seq.pat() instanceof Pat.Bind)
        ) {
          reporter.report(new ClausesProblem.UnsureCase(pos, ctor, dataCall));
          return null;
        }
      } else return null;
      // ^ If fails positively, this would be an impossible case
    } else conTele = conTele.map(param -> param.subst(matchy.get()));
    // Java wants a final local variable, let's alias it
    return conTele;
  }
}
