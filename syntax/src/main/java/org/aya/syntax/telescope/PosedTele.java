// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.telescope;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

public record PosedTele(@NotNull AbstractTele.Locns telescope, @NotNull ImmutableSeq<SourcePos> pos) {
  public PosedTele {
    assert telescope.telescopeSize() == pos.size();
  }

  public static @NotNull PosedTele fromSig(@NotNull Signature sig) {
    return new PosedTele(new AbstractTele.Locns(sig.rawParams(), sig.result()), sig.param().map(WithPos::sourcePos));
  }

  public @NotNull ImmutableSeq<Param> rawBoundParams() {
    return telescope.telescope();
  }

  public @NotNull ImmutableSeq<WithPos<Param>> boundParam() {
    return rawBoundParams().zip(pos, (p, s) -> new WithPos<>(s, p));
  }

  public @NotNull Term boundResult() {
    return telescope.result();
  }
}
