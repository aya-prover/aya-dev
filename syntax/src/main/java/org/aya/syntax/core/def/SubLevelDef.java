// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * Coconstructors or constructors, in contrast to {@link TopLevelDef}.
 */
public sealed abstract class SubLevelDef implements TyckDef permits ConDef {
  public final @NotNull ImmutableSeq<Param> ownerTele;
  public final @NotNull ImmutableSeq<Param> selfTele;
  public final @NotNull Term result;
  public final boolean coerce;

  protected SubLevelDef(
    @NotNull ImmutableSeq<Param> ownerTele,
    @NotNull ImmutableSeq<Param> selfTele,
    @NotNull Term result, boolean coerce
  ) {
    this.ownerTele = ownerTele;
    this.selfTele = selfTele;
    this.result = result;
    this.coerce = coerce;
  }

  public @NotNull SeqView<Param> fullTelescope() {
    return ownerTele.view().concat(selfTele);
  }

  @Override public @NotNull Term result() { return result; }
}
