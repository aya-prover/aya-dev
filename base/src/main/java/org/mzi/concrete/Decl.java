package org.mzi.concrete;

import org.jetbrains.annotations.NotNull;
import org.mzi.generic.Tele;

/**
 * concrete definition, corresponding to {@link org.mzi.core.def.Def}.
 * @author re-xyr
 */
public sealed interface Decl permits
  Decl.FnDecl {
  /**
   * concrete function definition, corresponding to {@link org.mzi.core.def.FnDef}.
   * @author re-xyr
   */
  record FnDecl(
    @NotNull String name,
    @NotNull Tele<Expr> telescope,
    @NotNull Expr result,
    @NotNull Expr body
  ) implements Decl {
  }
}
