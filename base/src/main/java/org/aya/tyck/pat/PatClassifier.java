// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl.DataCtor;
import org.aya.core.def.DataDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.error.MissingCaseError;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.control.Option;
import org.glavo.kala.tuple.primitive.IntObjTuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author ice1000, kiva
 */
public record PatClassifier(
  @NotNull Reporter reporter,
  @NotNull SourcePos pos,
  @NotNull PatTree.Builder builder
) {
  @TestOnly
  public static @NotNull ImmutableSeq<PatClass> testClassify(
    @NotNull ImmutableSeq<Pat.@NotNull Clause> clauses,
    @NotNull Reporter reporter, @NotNull SourcePos pos
  ) {
    return classify(clauses.map(Pat.PrototypeClause::prototypify), reporter, pos, true);
  }

  private static @NotNull ImmutableSeq<PatClass> classify(
    @NotNull ImmutableSeq<Pat.@NotNull PrototypeClause> clauses,
    @NotNull Reporter reporter, @NotNull SourcePos pos,
    boolean coverage
  ) {
    var classifier = new PatClassifier(reporter, pos, new PatTree.Builder());
    return classifier.classifySub(clauses.mapIndexed((index, clause) ->
      new SubPats(clause.patterns(), clause.expr(), index)), coverage);
  }

  public static void test(
    @NotNull ImmutableSeq<Pat.@NotNull PrototypeClause> clauses,
    @NotNull Reporter reporter, @NotNull SourcePos pos,
    boolean coverage
  ) {
    classify(clauses, reporter, pos, coverage);
  }

  /**
   * @param subPatsSeq should be of the same length, and should <strong>not</strong> be empty.
   * @param coverage   if true, in uncovered cases an error will be reported
   * @return pattern classes
   */
  private @NotNull ImmutableSeq<PatClass> classifySub(@NotNull ImmutableSeq<SubPats> subPatsSeq, boolean coverage) {
    assert !subPatsSeq.isEmpty();
    var pivot = subPatsSeq.first();
    // Done
    if (pivot.pats.isEmpty()) {
      var oneClass = subPatsSeq.map(subPats -> IntObjTuple2.of(subPats.ix, subPats.body));
      return ImmutableSeq.of(new PatClass(oneClass));
    }
    var explicit = pivot.head().explicit();
    var hasTuple = subPatsSeq.view()
      .mapIndexedNotNull((index, subPats) -> subPats.head() instanceof Pat.Tuple tuple
        ? flatTuple(tuple, index) : null)
      .toImmutableSeq();
    if (!hasTuple.isEmpty()) {
      builder.shiftEmpty(explicit);
      return classifySub(hasTuple, coverage);
    }
    var hasMatch = subPatsSeq.view()
      .mapNotNull(subPats -> subPats.head() instanceof Pat.Ctor ctor ? ctor.type() : null)
      .toImmutableSeq();
    // Progress
    if (hasMatch.isEmpty()) {
      builder.shiftEmpty(explicit);
      return classifySub(subPatsSeq.map(SubPats::drop), coverage);
    }
    // Here we have _some_ ctor patterns, therefore cannot be any tuple patterns.
    var buffer = Buffer.<PatClass>of();
    for (var ctor : hasMatch.first().availableCtors()) {
      var matches = subPatsSeq.view()
        .mapIndexedNotNull((ix, subPats) -> matches(subPats, ix, ctor.ref()))
        .toImmutableSeq();
      builder.shift(new PatTree(ctor.ref().name(), explicit));
      if (matches.isEmpty()) {
        if (coverage) {
          reporter.report(new MissingCaseError(pos, builder.root()));
          throw new ExprTycker.TyckInterruptedException();
        } else continue;
      }
      var classified = classifySub(matches, coverage);
      builder.reduce();
      var clazz = classified.flatMap(pat -> pat.extract(subPatsSeq).map(SubPats::drop));
      var rest = classifySub(clazz, coverage);
      builder.unshift();
      buffer.appendAll(rest);
    }
    return buffer.toImmutableSeq();
  }

  private @NotNull SubPats flatTuple(Pat.Tuple tuple, int index) {
    return new SubPats(tuple.pats(), Option.none(), index);
  }

  private static @Nullable SubPats matches(
    SubPats subPats, int ix,
    @NotNull DefVar<DataDef.Ctor, DataCtor> ref
  ) {
    var head = subPats.head();
    if (head instanceof Pat.Ctor ctor && ctor.ref() == ref)
      return new SubPats(ctor.params(), subPats.body, ix);
    if (head instanceof Pat.Bind bind) {
      var freshPat = ref.core.freshPat(bind.explicit());
      return new SubPats(freshPat.params(), subPats.body, ix);
    }
    return null;
  }

  public static record PatClass(@NotNull ImmutableSeq<IntObjTuple2<Option<Term>>> contents) {
    private @NotNull ImmutableSeq<SubPats> extract(@NotNull ImmutableSeq<SubPats> subPatsSeq) {
      return contents.map(tup -> subPatsSeq.get(tup._1));
    }
  }

  record SubPats(
    @NotNull SeqLike<Pat> pats,
    @NotNull Option<Term> body,
    int ix
  ) {
    @Contract(pure = true) public @NotNull Pat head() {
      return pats.first();
    }

    @Contract(pure = true) public @NotNull SubPats drop() {
      return new SubPats(pats.view().drop(1), body, ix);
    }
  }
}
