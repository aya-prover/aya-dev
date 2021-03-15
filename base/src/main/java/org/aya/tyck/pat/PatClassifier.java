// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.def.DataDef;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatToSubst;
import org.aya.core.visitor.Substituter;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.tuple.primitive.IntObjTuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000, kiva
 */
public interface PatClassifier {
  static @NotNull ImmutableSeq<PatClass> classify(@NotNull ImmutableSeq<Pat.@NotNull Clause> clauses) {
    return classifySub(clauses.mapIndexed((index, clause) ->
      new SubPats(clause.patterns(), new Substituter.TermSubst(new MutableHashMap<>()), index)));
  }

  /**
   * @param subPatsSeq should be of the same length, and should <strong>not</strong> be empty.
   * @return pattern classes
   */
  private static @NotNull ImmutableSeq<PatClass> classifySub(@NotNull ImmutableSeq<SubPats> subPatsSeq) {
    assert !subPatsSeq.isEmpty();
    var pivot = subPatsSeq.first();
    // Done
    if (pivot.pats.isEmpty()) {
      var oneClass = subPatsSeq.map(subPats -> IntObjTuple2.of(subPats.ix, subPats.bodySubst));
      return ImmutableSeq.of(new PatClass(oneClass));
    }
    var hasMatch = subPatsSeq.view()
      .mapNotNull(subPats -> subPats.head() instanceof Pat.Ctor ctor ? ctor.type() : null)
      .toImmutableSeq();
    // Progress
    if (hasMatch.isEmpty()) return classifySub(subPatsSeq.map(SubPats::drop));
    // Here we have _some_ ctor patterns, therefore cannot be any tuple patterns.
    return hasMatch.first().availableCtors()
      .map(ctor -> subPatsSeq.view()
        .mapIndexedNotNull((ix1, subPats) -> matches(subPats, ix1, ctor.ref()))
        .toImmutableSeq())
      .flatMap(PatClassifier::classifySub)
      .map(pats -> pats.extract(subPatsSeq).map(SubPats::drop))
      .flatMap(PatClassifier::classifySub)
      .toImmutableSeq();
  }

  private static @Nullable SubPats matches(
    SubPats subPats, int ix,
    @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref
  ) {
    var head = subPats.head();
    var bodySubst = subPats.bodySubst;
    if (head instanceof Pat.Ctor ctor && ctor.ref() == ref)
      return new SubPats(ctor.params(), bodySubst, ix);
    if (head instanceof Pat.Bind bind) {
      var freshPat = ref.core.freshPat(bind.explicit());
      bodySubst.add(bind.as(), freshPat.toTerm());
      return new SubPats(freshPat.params(), bodySubst, ix);
    }
    return null;
  }

  record PatClass(
    @NotNull ImmutableSeq<IntObjTuple2<Substituter.TermSubst>> contents
  ) {
    public @NotNull ImmutableSeq<SubPats> extract(@NotNull ImmutableSeq<SubPats> subPatsSeq) {
      return contents.map(tup -> {
        var pat = subPatsSeq.get(tup._1);
        pat.bodySubst.add(tup._2);
        return pat;
      });
    }
  }

  record SubPats(
    @NotNull SeqLike<Pat> pats,
    @NotNull Substituter.TermSubst bodySubst,
    int ix
  ) {
    @Contract(pure = true) public @NotNull Pat head() {
      return pats.first();
    }

    @Contract(pure = true) public @NotNull SubPats drop() {
      var subst = PatToSubst.build(pats.first());
      subst.map().putAll(bodySubst.map());
      return new SubPats(pats.view().drop(1), subst, ix);
    }
  }
}
