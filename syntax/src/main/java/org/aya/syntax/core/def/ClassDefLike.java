// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.*;
import org.jetbrains.annotations.NotNull;

public sealed interface ClassDefLike extends AnyDef permits JitClass, ClassDef.Delegate {
  @NotNull ImmutableSeq<? extends MemberDefLike> members();
  int classifyingIndex();

  default @NotNull MemberDefLike classifyingField() {
    assert classifyingIndex() > 0;
    return members().get(classifyingIndex());
  }

  default @NotNull Term telescope(int i, @NotNull Seq<Term> restriction) {
    var member = members().get(i);
    // Our code should not refer any out-of-scope field, the only meaningful part is [self.forget().take(i)]
    // Also, we don't use NewTerm, cause the type of the self-parameter is a class call without any restriction.
    var implList = ImmutableSeq.fill(members().size(), idx ->
      Closure.mkConst(idx < i ? restriction.get(idx) : ErrorTerm.DUMMY));

    var self = new ClassCastTerm(this, ErrorTerm.DUMMY, ImmutableSeq.empty(), implList);
    return member.signature().inst(ImmutableSeq.of(self)).makePi(Seq.empty());
  }

  default @NotNull SortTerm result(int implSize) {
    var members = members();
    assert implSize <= members.size();

    return members.view()
      .drop(implSize)
      .map(MemberDefLike::type)
      .foldLeft(SortTerm.Type0, DepTypeTerm::lubSigma);
  }
}
