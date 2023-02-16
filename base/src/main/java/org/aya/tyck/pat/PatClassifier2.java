// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.core.visitor.EndoTerm;
import org.aya.core.visitor.Subst;
import org.aya.tyck.error.TyckOrderError;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.tycker.StatedTycker;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.MCT.SubPats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Formerly known as <code>PatClassifier</code>.
 *
 * @author ice1000
 */
public final class PatClassifier2 extends StatedTycker {
  public final @NotNull SourcePos pos;
  private final PatTree.Builder builder;

  public PatClassifier2(
    @NotNull Reporter reporter, Trace.@Nullable Builder traceBuilder,
    @NotNull TyckState state, @NotNull SourcePos pos, @NotNull PatTree.Builder builder
  ) {
    super(reporter, traceBuilder, state);
    this.pos = pos;
    this.builder = builder;
  }

  public @NotNull ImmutableSeq<PatClass<ImmutableSeq<Arg<Term>>>> classifyN(
    @NotNull Subst subst, @NotNull SeqView<Term.Param> params,
    @NotNull ImmutableSeq<SubPats<Pat>> clauses, int fuel
  ) {
    var first = params.first();
    var cls = classify1(subst, first.subst(subst),
      clauses.map(it -> new SubPat(head(it), it.ix())), fuel);
    if (params.sizeEquals(1)) return cls.map(t -> t.map(ImmutableSeq::of));
    else return cls.flatMap(subclauses ->
      classifyN(subst.add(first.ref(), subclauses.term().term()),
        // Drop heads of both
        params.drop(1),
        // This is equivalent to (while being more efficient):
        //  extract(subclauses, clauses).map(SubPats::drop)
        extract(subclauses, clauses.map(SubPats::drop)), fuel)
        .map(args -> args.map(ls -> ls.prepended(subclauses.term))));
  }

  /**
   * @param pat already inlined
   * @param ix  see {@link SubPats#ix()}
   */
  record SubPat(@Nullable Pat pat, int ix) {
  }

  /**
   * @return Possibilities
   */
  @NotNull ImmutableSeq<PatClass<Arg<Term>>> classify1(
    @NotNull Subst subst, @NotNull Term.Param param,
    @NotNull ImmutableSeq<SubPat> clauses, int fuel
  ) {
    var whnfTy = whnf(param.type());
    var clsWithBindPat = clauses.view().mapIndexedNotNull((i, subPat) ->
        subPat.pat instanceof Pat.Bind ? i : null)
      .collect(ImmutableIntSeq.factory());
    final var explicit = param.explicit();
    switch (whnfTy) {
      default -> {
      }
      // Note that we cannot have ill-typed patterns such as constructor patterns,
      // since patterns here are already well-typed
      case SigmaTerm(var params) -> {
        // The type is sigma type, and do we have any non-catchall patterns?
        var clsWithTupPat = clauses.mapIndexedNotNull((i, subPat) ->
          subPat.pat instanceof Pat.Tuple tuple
            ? new SubPats<>(tuple.pats().view().map(Arg::term), i)
            : null);
        // In case we do,
        if (clsWithTupPat.isNotEmpty()) {
          builder.shift(new PatTree("", explicit, params.count(Term.Param::explicit)));
          params = new EndoTerm.Renamer().params(params.view());
          var classes = classifyN(subst.derive(), params.view(), clsWithTupPat, fuel);
          builder.reduce();
          return classes.map(args -> new PatClass<>(
            new Arg<>(new TupTerm(args.term), explicit),
            args.cls.appendedAll(clsWithBindPat)));
        }
      }
      case DataCall dataCall -> {
        // If there are no remaining clauses, probably it's due to a previous `impossible` clause,
        // but since we're going to remove this keyword, this check may not be needed in the future? LOL
        if (clauses.anyMatch(subPat -> subPat.pat != null) &&
          // there are no clauses starting with a constructor pattern -- we don't need a split!
          clauses.noneMatch(subPat -> subPat.pat instanceof Pat.Ctor || subPat.pat instanceof Pat.ShapedInt)
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
          builder.shift(new PatTree(ctor.ref().name(), explicit, conTele.count(Term.Param::explicit)));
          var matches = clauses.mapIndexedNotNull((ix, subPat) ->
            // Convert to constructor form
            (subPat.pat instanceof Pat.ShapedInt i
              ? i.constructorForm()
              : subPat.pat
              // Then turn into `SubPats`
            ) instanceof Pat.Ctor c && c.ref() == ctor.ref()
              ? new SubPats<>(c.params().view().map(Arg::term), ix)
              : null);
          var noMatch = matches.isEmpty() && clsWithBindPat.isEmpty();
          if (noMatch) {
            fuel1--;
            // In this case we give up and do not split on this constructor
            if (conTele.isEmpty() || fuel1 <= 0) {
              // TODO: report error
              builder.reduce();
              builder.unshift();
              continue;
            }
          }
          var classes = classifyN(subst.derive(), conTele.view(), matches, fuel1);
          builder.reduce();
          var conHead = dataCall.conHead(ctor.ref);
          buffer.appendAll(classes.map(args -> new PatClass<>(
            new Arg<>(new ConCall(conHead, args.term), explicit),
            args.cls.appendedAll(clsWithBindPat))));
          builder.unshift();
        }
        return buffer.toImmutableSeq();
      }
    }
    builder.shiftEmpty(explicit);
    builder.unshift();
    return ImmutableSeq.of(new PatClass<>(param.toArg(), clauses));
  }

  public record PatClass<T>(@NotNull T term, @NotNull ImmutableIntSeq cls) {
    PatClass(@NotNull T term, @NotNull ImmutableSeq<SubPat> cls) {
      this(term, cls.map(SubPat::ix).collect(ImmutableIntSeq.factory()));
    }

    public <R> @NotNull PatClass<R> map(Function<T, R> f) {
      return new PatClass<>(f.apply(term), cls);
    }
  }

  static <Pat> @NotNull ImmutableSeq<SubPats<Pat>> extract(
    PatClass<?> pats, @NotNull ImmutableSeq<SubPats<Pat>> subPatsSeq
  ) {
    return pats.cls.mapToObj(subPatsSeq::get);
  }

  private @Nullable SeqView<Term.Param>
  conTele(@NotNull ImmutableSeq<SubPat> clauses, DataCall dataCall, CtorDef ctor, @NotNull SourcePos pos) {
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
          clauses.noneMatch(seq -> seq.pat instanceof Pat.Bind)
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
