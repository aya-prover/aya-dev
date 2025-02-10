// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableSeq;
import org.aya.util.Pair;
import org.aya.util.position.SourceNode;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.ObjIntConsumer;

public interface ClassifierUtil<Subst, Term, Param, Pat> {
  Param subst(Subst subst, Param param);
  Pat normalize(Pat pat);
  Subst add(Subst subst, Term term);
  @ApiStatus.Internal @NotNull ImmutableSeq<PatClass.One<Term, Pat>> classify1(
    @NotNull Subst subst, @NotNull Param param,
    @NotNull ImmutableSeq<Indexed<Pat>> clauses, int fuel
  );

  @ApiStatus.Internal default @NotNull ImmutableSeq<PatClass.Seq<Term, Pat>> classifyN(
    @NotNull Subst subst, @NotNull SeqView<Param> telescope,
    @NotNull ImmutableSeq<Indexed<SeqView<Pat>>> clauses, int fuel
  ) {
    if (telescope.isEmpty()) return ImmutableSeq.of(new PatClass.Seq<>(Indexed.indices(clauses)));
    var first = telescope.getFirst();
    var cls = classify1(subst, subst(subst, first),
      clauses.mapIndexed((ix, it) ->
        new Indexed<>(normalize(it.pat().getFirst()), ix)), fuel);
    return cls.flatMap(subclauses ->
      classifyN(add(subst, subclauses.term()),
        // Drop heads of both
        telescope.drop(1),
        subclauses.extract(clauses.map(it ->
          new Indexed<>(it.pat().drop(1), it.ix()))), fuel)
        .map(args -> args.prepend(subclauses)));
  }

  @ApiStatus.Internal default @NotNull ImmutableSeq<PatClass.Two<Term, Pat>> classify2(
    @NotNull Subst subst, @NotNull Param tele1, @NotNull Function<Term, Param> tele2,
    @NotNull ImmutableSeq<Indexed<Pair<Pat, Pat>>> clauses, int fuel
  ) {
    var cls = classify1(subst, subst(subst, tele1),
      clauses.mapIndexed((ix, it) -> new Indexed<>(normalize(it.pat().component1()), ix)), fuel);
    return cls.flatMap(subclauses ->
      classify1(add(subst, subclauses.term()),
        tele2.apply(subclauses.term()),
        subclauses.extract(clauses.map(it ->
          new Indexed<>(it.pat().component2(), it.ix()))), fuel)
        .map(args -> args.pair(subclauses)));
  }

  static <T extends PatClass> MutableSeq<MutableList<T>> firstMatchDomination(
    @NotNull ImmutableSeq<? extends SourceNode> clauses,
    @NotNull ObjIntConsumer<SourcePos> report, @NotNull ImmutableSeq<T> classes
  ) {
    // StackOverflow says they're initialized to zero
    var numbers = MutableSeq.fill(clauses.size(), _ -> MutableList.<T>create());
    classes.forEach(results ->
      numbers.get(results.cls().min()).append(results));
    // ^ The minimum is not always the first one
    for (int i = 0; i < numbers.size(); i++)
      if (numbers.get(i).isEmpty()) report.accept(clauses.get(i).sourcePos(), i + 1);
    return numbers;
  }
}
