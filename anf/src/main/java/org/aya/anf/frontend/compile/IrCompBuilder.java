// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.mutable.MutableList;
import org.aya.anf.ir.struct.IrComp;
import org.aya.anf.ir.struct.LetClause;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/// CPS-style expression builder for the IR.
public class IrCompBuilder {
  private final @NotNull LoweringContext env;
  // XXX: currently this is a singleton list; introduce parallel moves
  private final @NotNull MutableList<LetClause> letBinds;

  public IrCompBuilder(final @NotNull LoweringContext env) {
    this.env = env;
    this.letBinds = MutableList.create();
  }

  public IrCompBuilder(@NotNull LoweringContext env, @NotNull LetClause clause) {
    this.env = env;
    this.letBinds = MutableList.of(clause);
  }

  public @NotNull IrComp make(@NotNull Function<LoweringContext, IrComp> builder) {
    letBinds.forEach(clause -> env.bindScope(clause.decl()));
    var res = builder.apply(env);
    letBinds.forEach(_ -> env.exitScope());
    return res;
  }
}
