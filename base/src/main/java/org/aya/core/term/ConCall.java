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
  @Override @NotNull ConCall.Head head,
  @Override @NotNull ImmutableSeq<Arg<Term>> conArgs
) implements ConCallLike {
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
}
