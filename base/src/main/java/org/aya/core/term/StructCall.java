// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.ClassDecl;
import org.aya.core.def.StructDef;
import org.aya.generic.Arg;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public record StructCall(
  @NotNull DefVar<StructDef, ClassDecl.StructDecl> ref,
  int ulift,
  @NotNull ImmutableSeq<Arg<@NotNull Term>> args
) implements CallTerm {
  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitStructCall(this, p);
  }
}
