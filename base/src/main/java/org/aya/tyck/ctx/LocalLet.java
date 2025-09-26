// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.ctx;

import kala.collection.mutable.MutableLinkedHashMap;
import kala.control.Option;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Scoped;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A locally, lazy substitution<br/>
 * Every substitution should be well-scoped, i.e.,
 * {@link Jdg} can only refer to some free variable or elder lazy substitution.
 */
public record LocalLet(
  @Override @Nullable LocalLet parent,
  @NotNull MutableLinkedHashMap<LocalVar, Jdg> let
) implements Scoped<LocalVar, Jdg, LocalLet> {
  public LocalLet() { this(null, MutableLinkedHashMap.of()); }
  @Override public @NotNull LocalLet self() { return this; }

  @Override public @NotNull LocalLet derive() {
    return derive(MutableLinkedHashMap.of());
  }

  public @NotNull LocalLet derive(@NotNull MutableLinkedHashMap<LocalVar, Jdg> let) {
    return new LocalLet(this, let);
  }

  @Override public @NotNull Option<Jdg> getLocal(@NotNull LocalVar key) {
    return let.getOption(key);
  }
  public @NotNull Term getTerm(@NotNull LocalVar key) {
    return get(key).wellTyped();
  }
  public boolean allFreeLocal() {
    return let.valuesView().allMatch(definedAs -> definedAs.wellTyped() instanceof FreeTerm);
  }
  @Override public void putLocal(@NotNull LocalVar key, @NotNull Jdg value) { let.put(key, value); }
}
