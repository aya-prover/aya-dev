// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.util.Arg;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.function.ObjIntConsumer;

public interface ClassifierUtil<Subst, Term, Param, Pat, Var> {
  Param subst(Subst subst, Param param);
  Pat normalize(Pat pat);
  Subst add(Subst subst, Var var, Term term);
  Var ref(Param param);
  @NotNull ImmutableSeq<PatClass<Arg<Term>>> classify1(
    @NotNull Subst subst, @NotNull Param param,
    @NotNull ImmutableSeq<Indexed<Pat>> clauses, int fuel
  );

  default @NotNull ImmutableSeq<PatClass<ImmutableSeq<Arg<Term>>>>
  classifyN(
    @NotNull Subst subst, @NotNull SeqView<Param> params,
    @NotNull ImmutableSeq<Indexed<SeqView<Pat>>> clauses, int fuel
  ) {
    if (params.isEmpty()) return ImmutableSeq.of(new PatClass<>(
      ImmutableSeq.empty(), Indexed.indices(clauses)));
    var first = params.getFirst();
    var cls = classify1(subst, subst(subst, first),
      clauses.mapIndexed((ix, it) -> new Indexed<>(normalize(it.pat().getFirst()), ix)), fuel);
    return cls.flatMap(subclauses ->
      classifyN(add(subst, ref(first), subclauses.term().term()),
        // Drop heads of both
        params.drop(1),
        subclauses.extract(clauses.map(it ->
          new Indexed<>(it.pat().drop(1), it.ix()))), fuel)
        .map(args -> args.map(ls -> ls.prepended(subclauses.term()))));
  }

  static int[] firstMatchDomination(
    @NotNull ImmutableSeq<? extends SourceNode> clauses,
    @NotNull ObjIntConsumer<SourcePos> report, @NotNull ImmutableSeq<? extends PatClass<?>> classes
  ) {
    // StackOverflow says they're initialized to zero
    var numbers = new int[clauses.size()];
    classes.forEach(results ->
      numbers[results.cls().min()]++);
    // ^ The minimum is not always the first one
    for (int i = 0; i < numbers.length; i++)
      if (0 == numbers[i]) report.accept(clauses.get(i).sourcePos(), i + 1);
    return numbers;
  }
}
