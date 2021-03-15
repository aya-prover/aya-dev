// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.core.visitor.Substituter;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record PatToSubst(@NotNull Substituter.TermSubst subst) implements Pat.Visitor<Unit, Unit> {
  public static @NotNull Substituter.TermSubst build(@NotNull Pat pat) {
    var subst = new PatToSubst(new Substituter.TermSubst(MutableHashMap.of()));
    pat.accept(subst, Unit.unit());
    return subst.subst;
  }

  @Override public Unit visitBind(Pat.@NotNull Bind bind, Unit unit) {
    return unit;
  }

  @Override public Unit visitTuple(Pat.@NotNull Tuple tuple, Unit unit) {
    if (tuple.as() != null) subst.add(tuple.as(), tuple.toTerm());
    tuple.pats().forEach(pat -> pat.accept(this, Unit.unit()));
    return unit;
  }

  @Override public Unit visitCtor(Pat.@NotNull Ctor ctor, Unit unit) {
    if (ctor.as() != null) subst.add(ctor.as(), ctor.toTerm());
    ctor.params().forEach(pat -> pat.accept(this, Unit.unit()));
    return unit;
  }
}
