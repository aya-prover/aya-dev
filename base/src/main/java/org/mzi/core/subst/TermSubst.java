package org.mzi.core.subst;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.ref.Ref;
import org.mzi.core.term.Term;

import java.util.Map;

public final class TermSubst {
  private final @NotNull Map<@NotNull Ref, @NotNull Term> subst;

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
