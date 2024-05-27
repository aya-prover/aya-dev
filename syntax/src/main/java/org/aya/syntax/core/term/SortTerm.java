// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.function.IndexedFunction;
import org.aya.generic.term.SortKind;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, tsao-chi
 */
public record SortTerm(@NotNull SortKind kind, int lift) implements StableWHNF, Formation {

  public SortTerm(@NotNull SortKind kind, int lift) {
    this.kind = kind;
    if (!kind.hasLevel() && lift != 0) throw new IllegalArgumentException("invalid lift");
    this.lift = lift;
  }

  public static final @NotNull SortTerm Type0 = new SortTerm(SortKind.Type, 0);
  public static final @NotNull SortTerm Set0 = new SortTerm(SortKind.Set, 0);
  public static final @NotNull SortTerm Set1 = new SortTerm(SortKind.Set, 1);
  public static final @NotNull SortTerm ISet = new SortTerm(SortKind.ISet, 0);

  /**
   * <a href="https://github.com/agda/agda/blob/6a92d584c70a615fdc3f364975814d75a0e31bf7/src/full/Agda/TypeChecking/Substitute.hs#L1541-L1558">Agda</a>
   */
  public @NotNull SortTerm succ() {
    return switch (kind) {
      case Type, Set -> new SortTerm(kind, lift + 1);
      case ISet -> Set1;
    };
  }

  @Override
  public @NotNull SortTerm doElevate(int lift) {
    return switch (kind) {
      case Type, Set -> new SortTerm(kind, this.lift + lift);
      case ISet -> lift == 1 ? Set1 : Set1.doElevate(lift - 1);
    };
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) { return this; }
}
