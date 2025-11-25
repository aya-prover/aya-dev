// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.states;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.control.Option;
import org.aya.generic.AyaDocile;
import org.aya.generic.Instance;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.ref.LocalCtx;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.tycker.Contextful;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// The case tree is used for eliminating meaningless comparison on [Term]s by compare the "type shape" first.
/// [post](https://amelia.how/posts/efficient-instance-resolution-for-agda.html)
public record InstanceCaseTree(@NotNull ExprTycker tycker) implements Stateful, Contextful {
  @Override public @NotNull LocalCtx localCtx() { return tycker.localCtx(); }
  @Override public @NotNull LocalCtx setLocalCtx(@NotNull LocalCtx ctx) {
    return tycker.setLocalCtx(ctx);
  }

  @Override public @NotNull TyckState state() { return tycker.state(); }

  /// @param idx     see [#splitTerm()]
  /// @param klauses clause of this case, can only overlap with wildcard
  public record Case(int idx, @NotNull ImmutableSeq<Clause> klauses) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.vcat(
        Doc.sep(Doc.plain("case"), Doc.plain(Integer.toString(idx)), Doc.plain("of")),
        Doc.vcat(klauses.map(it -> it.toDoc(options)))
      );
    }
  }

  /// @param pat  the "pattern" of this clause, it should be [org.aya.syntax.core.def.ClassDefLike] or [org.aya.syntax.core.def.DataDefLike]
  ///                                                null if wildcard
  /// @param kase the "body" of this clause
  public record Clause(@Nullable AnyDef pat, @NotNull Either<Case, ImmutableSeq<Done>> kase) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      var body = kase.fold(
        it -> it.toDoc(options),
        it -> Doc.commaList(it.map(done -> done.toDoc(options))));
      return Doc.sep(
        Doc.plain("|"), Doc.symbol(pat == null ? "*" : pat.name()), Doc.plain("=>"),
        Doc.nest(2, body)
      );
    }
  }

  public record Done(@NotNull Instance instance) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return instance.toDoc(options);
    }
  }

  public sealed interface SplitDef {
    @Nullable AnyDef head();
    @NotNull ImmutableSeq<Term> remains();

    record Rigid(@NotNull AnyDef head, int telescopeSize, @NotNull ImmutableSeq<Term> remains) implements SplitDef { }
    enum Flex implements SplitDef {
      INSTANCE;

      @Override public @Nullable AnyDef head() { return null; }
      @Override public @NotNull ImmutableSeq<Term> remains() { return ImmutableSeq.empty(); }
    }
  }

  /// This method split a def call to [SplitDef], a list of [SplitDef#remains()] is polluted.
  /// This is very similar to what we do when generating a case tree for constructor pattern,
  /// we add all it's subpattern to the pattern list.
  public @NotNull SplitDef splitTerm(@NotNull Term term) {
    return switch (term) {
      // TODO: fix null
      case ClassCall call -> new SplitDef.Rigid(call.ref(), call.args().size(), null);
      case DataCall call -> new SplitDef.Rigid(call.ref(), call.args().size(), call.args());
      // TODo: maybe also FnCall

      default -> SplitDef.Flex.INSTANCE;
    };
  }

  /// flatten a term into a sequence of [SplitDef]
  public @NotNull SeqView<SplitDef> flattenTerm(@NotNull Term term) {
    return switch (splitTerm(term)) {
      case SplitDef.Flex flex -> SeqView.of(flex);
      case SplitDef.Rigid rigid -> {
        // ... i believe this will be VERY slow
        var what = rigid.remains.flatMap(this::flattenTerm);
        yield what.view().prepended(rigid);
      }
    };
  }

  /// clause during case tree building
  public record Preclause(@NotNull SeqView<Term> remains, @NotNull Instance done) { }

  public @NotNull Case buildCaseTree(
    int idx,
    @NotNull ImmutableSeq<Preclause> preclauses
  ) {
    // None if wildcard
    var indexed = MutableLinkedHashMap.<Option<AnyDef>, MutableList<Preclause>>of();

    for (@Closed var preclause : preclauses) {
      var first = preclause.remains.getFirst();
      var remains = preclause.remains.drop(1);

      var split = splitTerm(first);
      var head = split.head();
      remains = split.remains().view().concat(remains);

      indexed.getOrPut(Option.ofNullable(head), FreezableMutableList::create)
        .append(new Preclause(remains, preclause.done));
    }

    // when can we have map on Map?
    var bodies = indexed.toSeq().map((pair) -> {
      var head = pair.component1();
      var candy = pair.component2();

      // candy is never empty
      var any = candy.getAny();
      if (candy.sizeEquals(1)) return new Clause(head.getOrNull(), Either.right(ImmutableSeq.of(new Done(any.done()))));

      // we may assume all call is full (due to our elaboration),
      // and all [Preclause] in preclauses come from the same call (assumption),
      // TODO ^ this may be wrong on ClassCall
      // thus all [Preclause#remains] in preclauses must have the same length (so can use [any])
      // however, not all [Preclause#done] are equal to each other
      // [done]s never duplicate, or in other words,
      // [done]s duplicates iff [preclauses#done]s duplicates
      if (any.remains.isEmpty()) return new Clause(head.getOrNull(), Either.right(candy.map(it -> new Done(it.done))));

      // unlike the article, we don't remove the matched term in the list, i think this is better (really?)
      var kase = buildCaseTree(idx + 1, candy.toSeq());
      return new Clause(head.getOrNull(), Either.left(kase));

      // we still keep case tree like: `case idx of { * -> ... }`, we will eliminate these in another pass.
    });

    return new Case(idx, bodies);
  }

  /// remove wildcard only case
  public static @NotNull Either<Case, ImmutableSeq<Done>> optimize(@NotNull Case kase) {
    if (kase.klauses.sizeEquals(1)) {
      var any = kase.klauses.getAny();
      if (any.pat == null) return any.kase;
    }

    // TODO: maybe descent (i.e. new only when effective update)
    var klauses = kase.klauses.map(k -> {
      if (k.kase.isRight()) return k;

      // I need a leftFlatMap
      return new Clause(k.pat, optimize(k.kase.getLeftValue()));
    });

    return Either.left(new Case(kase.idx, klauses));
  }
}
