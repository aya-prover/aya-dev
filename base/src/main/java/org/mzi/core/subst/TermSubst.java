// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.subst;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Var;
import org.mzi.core.term.Term;

import java.util.Collections;
import java.util.Map;

/**
 * @author ice1000
 */
public final class TermSubst {
  private final @NotNull Map<@NotNull Var, @NotNull Term> map;
  public static final @NotNull TermSubst EMPTY = new TermSubst(Collections.emptyMap());

  public TermSubst(@NotNull Map<@NotNull Var, @NotNull Term> map) {
    this.map = map;
  }

  public TermSubst(@NotNull Var var, @NotNull Term term) {
    this(Map.of(var, term));
  }

  @Contract(pure = true)
  public boolean isEmpty() {
    return map.isEmpty();
  }

  public void subst(@NotNull TermSubst subst) {
    if (map.isEmpty()) return;
    for (var entry : map.entrySet()) entry.setValue(entry.getValue().subst(subst));
  }

  public void addAll(@NotNull TermSubst subst) {
    map.putAll(subst.map);
  }

  public @Nullable Term get(@NotNull Var var) {
    return map.get(var);
  }

  public @NotNull Term get(@NotNull Var var, @NotNull Term defaultVal) {
    return map.getOrDefault(var, defaultVal);
  }

  public void clear() {
    map.clear();
  }

  public void remove(@NotNull Var var) {
    map.remove(var);
  }

  public void add(@NotNull Var var, @NotNull Term term) {
    subst(new TermSubst(var, term));
    map.put(var, term);
  }

  public void add(@NotNull TermSubst subst) {
    if (subst.isEmpty()) return;
    subst(subst);
    addAll(subst);
  }

  @Override
  public String toString() {
    return map.toString();
  }
}
