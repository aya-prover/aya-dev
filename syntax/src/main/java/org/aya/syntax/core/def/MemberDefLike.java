// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import org.aya.syntax.compile.JitMember;
import org.jetbrains.annotations.NotNull;

public sealed interface MemberDefLike extends AnyDef permits JitMember, MemberDef.Delegate {
  @NotNull ClassDefLike classRef();

  default int index() {
    var idx = classRef().members().indexOf(this);
    assert idx >= 0;
    return idx;
  }
}
