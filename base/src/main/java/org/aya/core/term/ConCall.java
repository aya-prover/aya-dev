// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.DataDef;
import org.aya.core.pat.Pat;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record ConCall(
  @NotNull ConCall.Head head,
  @NotNull ImmutableSeq<Arg<Term>> conArgs
) implements Callable.Tele {
  public @NotNull ConCall update(@NotNull Head head, @NotNull ImmutableSeq<Arg<Term>> conArgs) {
    return head == head() && conArgs.sameElements(conArgs(), true) ? this : new ConCall(head, conArgs);
  }

  @Override public @NotNull ConCall descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(head.descent(f), conArgs.map(arg -> arg.descent(f)));
  }

  public ConCall(
    @NotNull DefVar<DataDef, TeleDecl.DataDecl> dataRef,
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> dataArgs,
    int ulift,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> conArgs
  ) {
    this(new Head(dataRef, ref, ulift, dataArgs), conArgs);
  }

  @Override public @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref() {
    return head.ref;
  }

  @Override public int ulift() {
    return head.ulift;
  }

  @Override public @NotNull ImmutableSeq<Arg<@NotNull Term>> args() {
    return head.dataArgs.view().map(Arg::implicitify).concat(conArgs).toImmutableSeq();
  }

  /**
   * @param dataArgs the arguments to the data type, NOT the constructor patterns!!
   *                 They need to be turned implicit when used as arguments.
   * @see org.aya.tyck.pat.PatternTycker#mischa
   */
  public record Head(
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
}
