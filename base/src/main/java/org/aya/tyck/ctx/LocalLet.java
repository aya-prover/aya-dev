// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.ctx;

import kala.collection.mutable.MutableLinkedHashMap;
import kala.control.Option;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.util.Scoped;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * A locally, lazy substitution<br/>
 * Every substitution should be well-scoped, i.e.,
 * {@link Jdg} can only refer to some free variable or elder lazy substitution.
 */
public record LocalLet(
  @Override @Nullable LocalLet parent,
  @NotNull MutableLinkedHashMap<LocalVar, LocalLet.DefinedAs> let
) implements Scoped<LocalVar, LocalLet.DefinedAs, LocalLet> {
  /// @param inline whether this record should be immediately inlined when met.
  /// @see org.aya.tyck.StmtTycker#checkKitsune(DataCon, ExprTycker)
  /// @see org.aya.tyck.pat.ClauseTycker.LhsResult#dumpLocalLetTo
  public record DefinedAs(@NotNull @Closed Jdg definedAs, boolean inline) {
    public @NotNull DefinedAs map(@NotNull UnaryOperator<@Closed Jdg> f) {
      return new DefinedAs(f.apply(definedAs), inline);
    }
  }

  public LocalLet() { this(null, MutableLinkedHashMap.of()); }
  @Override public @NotNull LocalLet self() { return this; }

  @Override public @NotNull LocalLet derive() {
    return derive(MutableLinkedHashMap.of());
  }

  public @NotNull LocalLet derive(@NotNull MutableLinkedHashMap<LocalVar, LocalLet.DefinedAs> let) {
    return new LocalLet(this, let);
  }

  @Override public @NotNull Option<LocalLet.DefinedAs> getLocal(@NotNull LocalVar key) {
    return let.getOption(key);
  }
  public @NotNull Term getTerm(@NotNull LocalVar key) {
    return get(key).definedAs.wellTyped();
  }
  public boolean allFreeLocal() {
    return let.valuesView().allMatch(definedAs -> definedAs.definedAs.wellTyped() instanceof FreeTerm);
  }
  @Override public void putLocal(@NotNull LocalVar key, @NotNull LocalLet.DefinedAs value) { let.put(key, value); }

  public void put(@NotNull LocalVar key, @NotNull @Closed Jdg definedAs, boolean inline) {
    put(key, new DefinedAs(definedAs, inline));
  }
}
