// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.def;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.DefVar;
import org.mzi.concrete.Decl;
import org.mzi.core.term.Term;

/**
 * @author ice1000
 */
public record FnDef(
  @NotNull DefVar<FnDef, Decl.FnDecl> ref,
  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull Term result,
  @NotNull Term body
) implements Def {
  public FnDef {
    ref.core = this;
  }

  @Override
  public <P, R> R accept(Visitor<P, R> visitor, P p) {
    return visitor.visitFn(this, p);
  }
}
