// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * Top-level definitions.
 *
 * @author ice1000
 */
public sealed abstract class TopLevelDef<Ret extends Term> implements Def permits UserDef, PrimDef {
  public final @NotNull ImmutableSeq<Term.Param> telescope;
  public final @NotNull Ret result;

  protected TopLevelDef(
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Ret result
  ) {
    this.telescope = telescope;
    this.result = result;
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    return telescope;
  }

  @Override public @NotNull Ret result() {
    return result;
  }
}
