// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.generic.SortKind;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, tsao-chi
 */
public record SortTerm(@NotNull SortKind kind, int lift) implements StableWHNF {
  public SortTerm(@NotNull SortKind kind, int lift) {
    this.kind = kind;
    if (!kind.hasLevel() && lift != 0) throw new IllegalArgumentException("invalid lift");
    this.lift = lift;
  }

  public static SortTerm Type0 = new SortTerm(SortKind.Type, 0);
  public static SortTerm Set0 = new SortTerm(SortKind.Set, 0);
  public static SortTerm Set1 = new SortTerm(SortKind.Set, 1);
  public static SortTerm ISet = new SortTerm(SortKind.ISet, 0);
  public static SortTerm Prop = new SortTerm(SortKind.Prop, 0);

  public @NotNull SortTerm succ() {
    return switch (kind) {
      case Type, Set -> new SortTerm(kind, lift + 1);
      case Prop -> Type0;
      case ISet -> Set1;
    };
  }

  public @NotNull SortTerm elevate(int lift) {
    if (kind.hasLevel()) return new SortTerm(kind, this.lift + lift);
    else return this;
  }

  public boolean isProp() {
    return kind == SortKind.Prop;
  }
}
