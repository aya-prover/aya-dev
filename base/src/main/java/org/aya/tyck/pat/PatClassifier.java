// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.def.DataDef;
import org.aya.core.pat.Pat;
import org.aya.core.pat.PatToSubst;
import org.aya.core.term.Term;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class PatClassifier {
  /**
   * @param subPatsSeq should be of the same length, and should <strong>not</strong> be empty.
   */
  public static void classify(@NotNull ImmutableSeq<SubPats> subPatsSeq) {
    assert !subPatsSeq.isEmpty();
    var pivot = subPatsSeq.first();
    // Done
    if (pivot.pats.isEmpty()) return;
    var hasMatch = subPatsSeq.view()
      .mapNotNull(subPats -> subPats.head() instanceof Pat.Ctor ctor ? ctor.type() : null)
      .toImmutableSeq();
    // Progress
    if (hasMatch.isEmpty()) {
      classify(subPatsSeq.map(SubPats::drop));
      return;
    }
    // Here we have _some_ ctor patterns, therefore cannot be any tuple patterns.
    for (var ctor : hasMatch.first().availableCtors()) {
      var clazz = subPatsSeq.view()
        .filter(subPats -> matches(subPats, ctor.ref()))
        .toImmutableSeq();
    }
  }

  private static boolean matches(SubPats subPats, @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref) {
    var head = subPats.head();
    return head instanceof Pat.Ctor ctor && ctor.ref() == ref || head instanceof Pat.Bind;
  }

  public static record SubPats(
    @NotNull SeqLike<Pat> pats,
    @NotNull Option<Term> body,
    int ix
  ) {
    @Contract(pure = true) public @NotNull Pat head() {
      return pats.first();
    }

    @Contract(pure = true) public @NotNull SubPats drop() {
      var subst = PatToSubst.build(pats.first());
      return new SubPats(pats.view().drop(1), body.map(term -> term.subst(subst)), ix);
    }
  }
}
