// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.core.pat.Pat;
import org.aya.generic.SortKind;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author ice1000, tsao-chi
 */
public record SortTerm(@NotNull SortKind kind, int lift) implements StableWHNF, Formation {
  @Override public @NotNull SortTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return this;
  }

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

  public @NotNull SortTerm elevate(int lift) {
    if (kind.hasLevel()) return new SortTerm(kind, this.lift + lift);
    else return this;
  }
}
