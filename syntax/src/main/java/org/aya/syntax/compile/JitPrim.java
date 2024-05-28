// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import org.aya.generic.stmt.Reducible;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public abstract non-sealed class JitPrim extends JitDef implements Reducible {
  public final PrimDef.ID id;
  protected JitPrim(int telescopeSize, boolean[] telescopeLicit, String[] telescopeName, PrimDef.ID id) {
    super(telescopeSize, telescopeLicit, telescopeName);
    this.id = id;
  }

  /**
   * Unfold this function
   */
  @Override public abstract Term invoke(Term stuck, @NotNull Seq<Term> args);
}
