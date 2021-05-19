// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.Var;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.glavo.kala.collection.Map;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.glavo.kala.collection.mutable.MutableTreeMap;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * This doesn't substitute references underlying function calls.
 *
 * @author ice1000
 */
public record Substituter(
  @NotNull Map<Var, Term> termSubst,
  @NotNull LevelSubst levelSubst
) implements TermFixpoint<Unit> {
  public Substituter(@NotNull TermSubst termSubst, @NotNull LevelSubst levelSubst) {
    this(termSubst.map, levelSubst);
  }

  @Override public @NotNull Sort.CoreLevel visitLevel(@NotNull Sort.CoreLevel sort, Unit unit) {
    return levelSubst.applyTo(sort);
  }

  @Override public @NotNull Term visitRef(@NotNull RefTerm term, Unit unused) {
    return termSubst.getOrDefault(term.var(), term);
  }

  /**
   * @author ice1000
   */
  @Debug.Renderer(text = "map.toString()",
    childrenArray = "map.asJava().entrySet().toArray()",
    hasChildren = "map.isNotEmpty()")
  public static record TermSubst(@NotNull MutableMap<@NotNull Var, @NotNull Term> map) {
    public static final @NotNull TermSubst EMPTY = new TermSubst(MutableTreeMap.of((o1, o2) -> {
      throw new UnsupportedOperationException("Shall not modify LevelSubst.EMPTY");
    }));

    public TermSubst(@NotNull Var var, @NotNull Term term) {
      this(MutableHashMap.of(var, term));
    }

    public void subst(@NotNull TermSubst subst) {
      if (map.isEmpty()) return;
      map.replaceAll((var, term) -> term.subst(subst));
    }

    public @NotNull TermSubst add(@NotNull Var var, @NotNull Term term) {
      subst(new TermSubst(var, term));
      map.put(var, term);
      return this;
    }

    public @NotNull TermSubst add(@NotNull TermSubst subst) {
      if (subst.map.isEmpty()) return this;
      subst(subst);
      map.putAll(subst.map);
      return this;
    }

    public void clear() {
      map.clear();
    }
  }
}
