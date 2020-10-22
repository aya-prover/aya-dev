package org.mzi.core.subst;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Ref;
import org.mzi.core.term.Term;

import java.util.Collections;
import java.util.Map;

/**
 * @author ice1000
 */
public final class TermSubst {
  private final @NotNull Map<@NotNull Ref, @NotNull Term> map;
  public static final @NotNull TermSubst EMPTY = new TermSubst(Collections.emptyMap());

  public TermSubst(@NotNull Map<@NotNull Ref, @NotNull Term> map) {
    this.map = map;
  }

  public TermSubst(@NotNull Ref ref, @NotNull Term term) {
    this(Map.of(ref, term));
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

  public @Nullable Term get(@NotNull Ref ref) {
    return map.get(ref);
  }

  public @NotNull Term get(@NotNull Ref ref, @NotNull Term defaultVal) {
    return map.getOrDefault(ref, defaultVal);
  }

  public void clear() {
    map.clear();
  }

  public void remove(@NotNull Ref ref) {
    map.remove(ref);
  }

  public void add(@NotNull Ref ref, @NotNull Term term) {
    subst(new TermSubst(ref, term));
    map.put(ref, term);
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
