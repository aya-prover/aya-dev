// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;
import org.mzi.generic.Tele;
import org.mzi.ref.DefVar;

/**
 * concrete definition, corresponding to {@link org.mzi.core.def.Def}.
 *
 * @author re-xyr
 */
public sealed interface Decl {
  @Contract(pure = true) @NotNull SourcePos sourcePos();
  @Contract(pure = true) @NotNull DefVar<? extends Decl> ref();

  /**
   * concrete function definition, corresponding to {@link org.mzi.core.def.FnDef}.
   * @author re-xyr
   */
  final class FnDecl implements Decl {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull DefVar<FnDecl> ref;
     public final @NotNull Tele<Expr> telescope;
     public final @NotNull Expr result;
     public final @NotNull Expr body;

     public FnDecl(@NotNull SourcePos sourcePos, @NotNull String name, @NotNull Tele<Expr> telescope, @NotNull Expr result, @NotNull Expr body) {
       this.sourcePos = sourcePos;
       this.ref = new DefVar<>(this, name);
       this.telescope = telescope;
       this.result = result;
       this.body = body;
     }

     public @NotNull SourcePos sourcePos() {
       return this.sourcePos;
     }

     public @NotNull DefVar<FnDecl> ref() {
       return this.ref;
     }
  }
}
