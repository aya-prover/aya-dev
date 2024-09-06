// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ClassCall;
import org.jetbrains.annotations.NotNull;

public sealed interface ClassDefLike extends AnyDef permits JitClass, ClassDef.Delegate {
  @NotNull ImmutableSeq<MemberDefLike> members();

  default @NotNull Term telescope(int i, @NotNull Seq<Term> restriction) {
    var member = members().get(i);
    // Our code should not refer the subterm of self, the only meaningful part is [self.forget()]
    // Also, we don't use NewTerm, cause the type of the self-parameter is a class call without any restriction.
    var self = new ClassCastTerm(this, ErrorTerm.DUMMY, ImmutableSeq.empty(),
      restriction.map(Closure::mkConst)
    );

    return member.signature().inst(ImmutableSeq.of(self)).makePi(Seq.empty());
  }

  default @NotNull SortTerm result(int implSize) {
    var members = members();
    assert implSize <= members.size();

    return members.view()
      .drop(implSize)
      .map(MemberDefLike::type)
      .foldLeft(SortTerm.Type0, SigmaTerm::lub);
  }
}
