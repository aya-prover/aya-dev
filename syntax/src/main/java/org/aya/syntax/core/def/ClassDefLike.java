// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.core.term.SigmaTerm;
import org.aya.syntax.core.term.SortTerm;
import org.jetbrains.annotations.NotNull;

public sealed interface ClassDefLike extends AnyDef permits JitClass, ClassDef.Delegate {
  @NotNull ImmutableSeq<MemberDefLike> members();

  default @NotNull SortTerm result(int implSize) {
    var members = members();
    assert implSize <= members.size();

    return members.view()
      .drop(implSize)
      .map(MemberDefLike::type)
      .foldLeft(SortTerm.Type0, SigmaTerm::lub);
  }
}
