// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.generic.Constants;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.PiTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Indicating something is {@link LocalCtx}ful.<br/>
 * Whenever you want to introduce some bind, make sure you are modifying
 * the {@link LocalCtx} that you own it, i.e. obtained from {@link Contextful#subscoped}.
 * In fact, this is the rule of ownership 🦀🦀🦀.<br/>
 *
 * @see #subscoped(Supplier)
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

  @Contract(mutates = "this")
  default <R> R subscoped(@NotNull Supplier<R> action) {
    var parentCtx = setLocalCtx(localCtx().derive());
    var result = action.get();
    setLocalCtx(parentCtx);
    return result;
  }

  @Contract(mutates = "this")
  default <R> R with(@NotNull LocalVar var, @NotNull Term type, @NotNull Supplier<R> action) {
    return subscoped(() -> {
      localCtx().put(var, type);
      return action.get();
    });
  }

  /**
   * Generate a fresh {@link MetaCall} with type {@link Param#type()}
   */
  default @NotNull Term mockTerm(@NotNull Param param, @NotNull SourcePos pos) {
    return freshMeta(param.name(), pos, new MetaVar.OfType(param.type()));
  }

  /**
   * Construct a fresh {@link MetaCall}
   *
   * @see LocalCtx#extract()
   */
  default @NotNull MetaCall freshMeta(String name, @NotNull SourcePos pos, MetaVar.Requirement req) {
    var vars = localCtx().extract().toImmutableSeq();
    var args = vars.<Term>map(FreeTerm::new);
    return new MetaCall(new MetaVar(name, pos, args.size(), req.bind(vars.view())), args);
  }

  default @NotNull Term generatePi(Expr.@NotNull Lambda expr, SourcePos sourcePos) {
    var param = expr.param();
    return generatePi(sourcePos, param.ref().name());
  }

  private @NotNull Term generatePi(@NotNull SourcePos pos, @NotNull String name) {
    var genName = name + Constants.GENERATED_POSTFIX;
    var domain = freshMeta(STR."\{genName}ty", pos, MetaVar.Misc.IsType);
    var codomain = freshMeta(STR."\{genName}ret", pos, MetaVar.Misc.IsType);
    return new PiTerm(domain, Closure.mkConst(codomain));
  }
}
