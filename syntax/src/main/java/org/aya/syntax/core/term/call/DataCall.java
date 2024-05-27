// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.concrete.stmt.decl.DataDecl;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public record DataCall(
  @Override @NotNull DataDefLike ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<@NotNull Term> args
) implements Callable.Tele, StableWHNF, Formation {
  public DataCall(
    @NotNull DefVar<DataDef, DataDecl> ref,
    int ulift,
    @NotNull ImmutableSeq<@NotNull Term> args
  ) {
    this(new DataDef.Delegate(ref), ulift, args);
  }

  public @NotNull DataCall update(@NotNull ImmutableSeq<Term> args) {
    return args.sameElements(args(), true) ? this : new DataCall(ref, ulift, args);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(Callable.descent(args, f));
  }

  @Override public @NotNull Tele doElevate(int level) {
    return new DataCall(ref, ulift + level, args);
  }

  public @NotNull ConCallLike.Head conHead(@NotNull ConDefLike conRef) {
    return new ConCallLike.Head(conRef, ulift, args);
  }
}
