// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.generic.term.DTKind;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.MetaVar;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Indicating something is {@link LocalCtx}ful.<br/>
 * Most of the useful methods are in {@link AbstractTycker}.
 *
 * @see #localCtx()
 */
public interface Contextful {
  @NotNull LocalCtx localCtx();

  /**
   * Update {@code localCtx} with the given one
   *
   * @param ctx new {@link LocalCtx}
   * @return old context
   */
  @ApiStatus.Internal
  @Contract(mutates = "this")
  @NotNull LocalCtx setLocalCtx(@NotNull LocalCtx ctx);

  /**
   * Generate a fresh {@link MetaCall} with type {@link Param#type()}
   */
  default @NotNull MetaCall mockTerm(@NotNull Param param, @NotNull SourcePos pos) {
    return freshMeta(param.name(), pos, new MetaVar.OfType(param.type()), false);
  }

  /**
   * Construct a fresh {@link MetaCall}
   *
   * @see LocalCtx#extract()
   */
  default @NotNull MetaCall freshMeta(String name, @NotNull SourcePos pos, MetaVar.Requirement req, boolean isUser) {
    var vars = localCtx().extract().toImmutableSeq();
    var args = vars.<Term>map(FreeTerm::new);
    return new MetaCall(new MetaVar(name, pos, args.size(), req.bind(vars.view()), isUser), args);
  }

  /** @see org.aya.syntax.ref.MetaVar#asDt */
  default @NotNull Term generatePi(Expr.@NotNull Lambda expr, SourcePos sourcePos) {
    return generatePi(sourcePos, expr.ref().name());
  }

  private @NotNull Term generatePi(@NotNull SourcePos pos, @NotNull String name) {
    var domain = freshMeta(name + "ty", pos, MetaVar.Misc.IsType, false);
    var codomain = freshMeta(name + "ret", pos, MetaVar.Misc.IsType, false);
    return new DepTypeTerm(DTKind.Pi, domain, Closure.mkConst(codomain));
  }
}
