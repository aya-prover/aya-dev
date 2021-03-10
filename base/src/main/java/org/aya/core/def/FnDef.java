// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.term.Term;
import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record FnDef(
  @NotNull DefVar<FnDef, Decl.FnDecl> ref,
  @NotNull Seq<Term.Param> telescope,
  @NotNull Term result,
  @NotNull Term body
) implements Def {
  public FnDef {
    ref.core = this;
  }

  @Override
  public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitFn(this, p);
  }
}
