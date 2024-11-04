// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import org.aya.syntax.compile.JitMember;
import org.aya.syntax.core.term.SortTerm;
import org.jetbrains.annotations.NotNull;

public sealed interface MemberDefLike extends AnyDef permits JitMember, MemberDef.Delegate {
  @NotNull ClassDefLike classRef();

  /**
   * The type of the type of this member, not include self-parameter
   */
  @NotNull SortTerm type();

  int index();
}
