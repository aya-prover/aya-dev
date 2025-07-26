// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.immutable.ImmutableSeq;
import org.aya.anf.ir.struct.IrComp;
import org.aya.anf.ir.struct.LetClause;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/// CPS-style expression builder for the IR.
public class IrCompBuilder {
  private final @NotNull LoweringContext ctx;
  // XXX: currently this is a singleton list; introduce parallel moves
  private final @NotNull ImmutableSeq<LetClause> letBinds;

  public IrCompBuilder(final @NotNull LoweringContext ctx) {
    this.ctx = ctx;
    this.letBinds = ImmutableSeq.empty();
  }

  public IrCompBuilder(@NotNull LoweringContext ctx, @NotNull LetClause clause) {
    this.ctx = ctx;
    this.letBinds = ImmutableSeq.of(clause);
  }

  private @NotNull IrComp make(@NotNull Function<LoweringContext, IrComp> builder) {
    letBinds.forEach(clause -> ctx.bindScope(clause.decl()));
    var res = builder.apply(ctx);
    letBinds.forEach(_ -> ctx.exitScope());
    return res;
  }

  public @NotNull IrComp make(@NotNull Term term) {
    IrComp result;
    switch (term) {
      case FreeTerm(var v) -> result = new IrComp.Val(ctx.lookup(v).newRef());
      default -> throw new Panic("Not implemented term: " + term.easyToString());
    }
    return letBinds.foldRight(result, IrComp.Let::new);
  }
}
