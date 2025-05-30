// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * Top-level definitions.
 */
public sealed interface TopLevelDef extends TyckDef permits ClassDef, DataDef, FnDef, PrimDef {
  @Override default @NotNull ImmutableSeq<Param> telescope() {
    var signature = ref().signature;
    assert signature != null : ref().name();
    return signature.params();
  }

  @Override default @NotNull Term result() {
    var signature = ref().signature;
    assert signature != null : ref().name();
    return signature.result();
  }
}
