// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.mutable.MutableMap;
import org.aya.core.term.PrimTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.ref.LocalVar;
import org.aya.ref.Var;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** guest0x0 note says interval context can be separated */
@Debug.Renderer(text = "map.toString()",
  childrenArray = "map.asJava().entrySet().toArray()",
  hasChildren = "map.isNotEmpty()")
public record IntervalSubst(
  @NotNull MutableMap<Var, Term> map
) implements CofThy.SubstObj<Term, LocalVar, IntervalSubst> {
  public IntervalSubst() {
    this(MutableMap.create());
  }

  @Override public void put(LocalVar i, boolean isLeft) {
    map.put(i, isLeft ? PrimTerm.End.LEFT : PrimTerm.End.RIGHT);
  }

  @Override public boolean contradicts(LocalVar i, boolean newIsLeft) {
    // TODO: formula
    // In an and-only cofibration, every variable appears uniquely in a cond.
    return map.containsKey(i);
  }

  @Override public @Nullable LocalVar asRef(@NotNull Term term) {
    return term instanceof RefTerm ref ? ref.var() : null;
  }

  @Override public @NotNull IntervalSubst derive() {
    return new IntervalSubst(MutableMap.from(map));
  }
}
