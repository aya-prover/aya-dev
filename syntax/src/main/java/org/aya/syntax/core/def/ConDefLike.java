// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.CompiledVar;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public sealed interface ConDefLike extends AnyDef permits JitCon, ConDef.Delegate {
  @NotNull DataDefLike dataRef();

  /** @return true if this is a path constructor */
  boolean hasEq();
  @NotNull Term equality(Seq<Term> args, boolean is0);
  int selfTeleSize();
  int ownerTeleSize();
  @NotNull ImmutableSeq<Param> selfTele(@NotNull ImmutableSeq<Term> ownerArgs);

  @SuppressWarnings("unchecked") static @NotNull ConDefLike from(@NotNull AnyDefVar var) {
    return switch (var) {
      case DefVar<?, ?> dv -> new ConDef.Delegate((DefVar<ConDef, ?>) dv);
      case CompiledVar cv -> (ConDefLike) cv.core();
    };
  }
}
