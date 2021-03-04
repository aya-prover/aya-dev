// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.Var;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.tyck.sort.LevelSubst;
import org.aya.tyck.sort.Sort;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * This doesn't substitute references underlying function calls.
 *
 * @author ice1000
 */
public record Substituter(
  @NotNull TermSubst termSubst, @NotNull LevelSubst levelSubst) implements TermFixpoint<Unit> {
  @Override public @NotNull Sort visitSort(@NotNull Sort sort, Unit unused) {
    return sort.substSort(levelSubst);
  }

  @Override public @NotNull Term visitRef(@NotNull RefTerm term, Unit unused) {
    return termSubst.map.getOrDefault(term.var(), term);
  }

  /**
   * @author ice1000
   */
  public static record TermSubst(@NotNull MutableMap<@NotNull Var, @NotNull Term> map) {
    // TODO[JDK-8247334]: uncomment when we move to JDK16
    public static final /*@NotNull*/ TermSubst EMPTY = new TermSubst(MutableHashMap.of());

    public TermSubst(@NotNull Var var, @NotNull Term term) {
      this(MutableHashMap.of(var, term));
    }

    public void subst(@NotNull TermSubst subst) {
      if (map.isEmpty()) return;
      map.updateAll((var, term) -> term.subst(subst));
    }

    public void addAll(@NotNull TermSubst subst) {
      map.putAll(subst.map);
    }

    public void add(@NotNull Var var, @NotNull Term term) {
      subst(new TermSubst(var, term));
      map.put(var, term);
    }

    public void add(@NotNull TermSubst subst) {
      if (subst.map.isEmpty()) return;
      subst(subst);
      addAll(subst);
    }
  }
}
