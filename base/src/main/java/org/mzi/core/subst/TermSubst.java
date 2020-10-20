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
  private final @NotNull Map<@NotNull Ref, @NotNull Term> subst;
  public static final @NotNull TermSubst EMPTY = new TermSubst(Collections.emptyMap());

  public TermSubst(@NotNull Map<@NotNull Ref, @NotNull Term> subst) {
    this.subst = subst;
  }

  public TermSubst(@NotNull Ref ref, @NotNull Term term) {
    this(Map.of(ref, term));
  }

  @Contract(pure = true)
  public boolean isEmpty() {
    return subst.isEmpty();
  }

  public void subst(@NotNull TermSubst termSubst) {
    if (subst.isEmpty()) return;
    for (var entry : subst.entrySet()) entry.setValue(entry.getValue().subst(termSubst));
  }

  public void addAll(@NotNull TermSubst termSubst) {
    subst.putAll(termSubst.subst);
  }

  public @Nullable Term get(@NotNull Ref ref) {
    return subst.get(ref);
  }

  public @NotNull Term get(@NotNull Ref ref, @NotNull Term defaultVal) {
    return subst.getOrDefault(ref, defaultVal);
  }

  public void clear() {
    subst.clear();
  }

  public void remove(@NotNull Ref ref) {
    subst.remove(ref);
  }

  public void add(@NotNull Ref ref, @NotNull Term term) {
    subst(new TermSubst(ref, term));
    subst.put(ref, term);
  }

  public void add(@NotNull TermSubst termSubst) {
    if (termSubst.isEmpty()) return;
    subst(termSubst);
    addAll(termSubst);
  }

  @Override
  public String toString() {
    return subst.toString();
  }
}
