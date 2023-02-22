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

public record DataCall(
  @Override @NotNull DefVar<DataDef, TeleDecl.DataDecl> ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
) implements Callable.Tele, StableWHNF, Formation {
  public @NotNull DataCall update(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.sameElements(args(), true) ? this : new DataCall(ref, ulift, args);
  }

  @Override public @NotNull DataCall descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(args.map(arg -> arg.descent(f)));
  }

  public @NotNull ConCall.Head conHead(@NotNull DefVar<CtorDef, TeleDecl.DataCtor> ctorRef) {
    return new ConCall.Head(ref, ctorRef, ulift, args);
  }
}
