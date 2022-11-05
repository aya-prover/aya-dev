// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.generic.SortKind;
import org.jetbrains.annotations.NotNull;

// TODO[ice, for tsao-chi] Refactor `Sort` and lift it to top-level
//  and remove `FormTerm`
public sealed interface FormTerm extends Term permits PartialTyTerm, PathTerm, PiTerm, SigmaTerm {

  /**
   * @author ice1000
   */
  sealed interface Sort extends StableWHNF {
    int lift();
    @NotNull SortKind kind();
    @NotNull FormTerm.Sort succ();

    static @NotNull Sort create(@NotNull SortKind kind, int lift) {
      return switch (kind) {
        case Type -> new Type(lift);
        case Set -> new Set(lift);
        case Prop -> Prop.INSTANCE;
        case ISet -> ISet.INSTANCE;
      };
    }
  }

  record Type(@Override int lift) implements Sort {
    public static final @NotNull FormTerm.Type ZERO = new Type(0);

    @Override public @NotNull SortKind kind() {
      return SortKind.Type;
    }

    @Override public @NotNull FormTerm.Type succ() {
      return new FormTerm.Type(lift + 1);
    }
  }

  record Set(@Override int lift) implements Sort {
    public static final @NotNull FormTerm.Set ZERO = new Set(0);

    @Override public @NotNull SortKind kind() {
      return SortKind.Set;
    }

    @Override
    public @NotNull FormTerm.Set succ() {
      return new FormTerm.Set(lift + 1);
    }
  }

  final class Prop implements Sort {
    public static final @NotNull Prop INSTANCE = new Prop();

    private Prop() {
    }

    @Override public int lift() {
      return 0;
    }

    @Override public @NotNull SortKind kind() {
      return SortKind.Prop;
    }

    @Override public @NotNull FormTerm.Type succ() {
      return new FormTerm.Type(0);
    }
  }

  final class ISet implements Sort {
    public static final @NotNull ISet INSTANCE = new ISet();

    private ISet() {

    }

    @Override public int lift() {
      return 0;
    }

    @Override public @NotNull SortKind kind() {
      return SortKind.ISet;
    }

    @Override public @NotNull FormTerm.Set succ() {
      return new FormTerm.Set(1);
    }
  }
}
