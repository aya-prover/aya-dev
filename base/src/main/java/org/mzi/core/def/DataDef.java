// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.def;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.DefVar;
import org.mzi.concrete.Decl;
import org.mzi.core.Param;

public record DataDef(
  @NotNull DefVar<FnDef, Decl.FnDecl> ref,
  @NotNull ImmutableSeq<Param> telescope
  // TODO: add other information
  //  See also org.mzi.core.visitor.RefFinder.visitData
) implements Def {
  @Override public <P, R> R accept(Visitor<P, R> visitor, P p) {
    return visitor.visitData(this, p);
  }
}
