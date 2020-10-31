// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.visitor;

import asia.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.term.RefTerm;
import org.mzi.core.term.Term;
import org.mzi.tyck.sort.LevelSubst;
import org.mzi.tyck.sort.Sort;

import java.util.Collections;
import java.util.Map;

/**
 * This doesn't substitute references underlying function calls.
 *
 * @author ice1000
 */
public record SubstFixpoint(
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
  public static record TermSubst(@NotNull Map<@NotNull Var, @NotNull Term> map) {
    // TODO[JDK-8247334]: uncomment when we move to JDK16
    public static final /*@NotNull*/ TermSubst EMPTY = new TermSubst(Collections.emptyMap());
    public TermSubst(@NotNull Var var, @NotNull Term term) {
      this(Map.of(var, term));
    }

    public void subst(@NotNull TermSubst subst) {
      if (map.isEmpty()) return;
      for (var entry : map.entrySet())
        entry.setValue(entry.getValue().subst(subst));
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
