// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record FnDef(
  @NotNull DefVar<FnDef, Decl.FnDecl> ref,
  @NotNull ImmutableSeq<Term.Param> contextTele,

  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull Term result,
  @NotNull Either<Term, ImmutableSeq<Pat.Clause>> body
) implements Def {
  public FnDef {
    ref.core = this;
  }

  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitFn(this, p);
  }
}
