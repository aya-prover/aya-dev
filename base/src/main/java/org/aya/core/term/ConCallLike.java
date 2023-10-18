// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.DataDef;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public sealed interface ConCallLike extends Callable.Tele permits ConCall, IntegerTerm, ReduceRule.Con {
  /**
   * @param dataArgs the arguments to the data type, NOT the constructor patterns!!
   *                 They need to be turned implicit when used as arguments.
   * @see org.aya.tyck.pat.PatternTycker#mischa
   */
  record Head(
    @NotNull DefVar<DataDef, TeleDecl.DataDecl> dataRef,
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    int ulift,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> dataArgs
  ) {
    public @NotNull DataCall underlyingDataCall() {
      return new DataCall(dataRef, ulift, dataArgs);
    }

    public @NotNull Head descent(@NotNull UnaryOperator<@NotNull Term> f) {
      var args = dataArgs.map(arg -> arg.descent(f));
      if (args.sameElements(dataArgs, true)) return this;
      return new Head(dataRef, ref, ulift, args);
    }
  }

  @NotNull ConCallLike.Head head();
  @NotNull ImmutableSeq<Arg<Term>> conArgs();

  @Override
  default @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref() {
    return head().ref();
  }

  @Override
  default @NotNull ImmutableSeq<Arg<@NotNull Term>> args() {
    return head().dataArgs().view()
      .map(Arg::implicitify)
      .concat(conArgs())
      .toImmutableSeq();
  }

  @Override
  default int ulift() {
    return head().ulift();
  }
}
