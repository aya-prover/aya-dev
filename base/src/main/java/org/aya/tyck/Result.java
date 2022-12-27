// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.core.term.ErrorTerm;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.tycker.TyckState;
import org.jetbrains.annotations.NotNull;

public sealed interface Result {
  @NotNull Term wellTyped();
  @NotNull Term type();
  @NotNull Result freezeHoles(@NotNull TyckState state);
  default @NotNull Result normalize(@NotNull NormalizeMode mode, @NotNull TyckState state) {
    return new Default(wellTyped().normalize(state, mode), type().normalize(state, mode));
  }
  /**
   * {@link Default#type} is the type of {@link Default#wellTyped}.
   *
   * @author ice1000
   */
  record Default(@Override @NotNull Term wellTyped, @Override @NotNull Term type) implements Result {
    public static @NotNull Default error(@NotNull AyaDocile description) {
      return new Default(ErrorTerm.unexpected(description), ErrorTerm.typeOf(description));
    }

    @Override public @NotNull Default freezeHoles(@NotNull TyckState state) {
      return new Default(wellTyped.freezeHoles(state), type.freezeHoles(state));
    }
  }

  record Sort(@Override @NotNull SortTerm wellTyped) implements Result {
    @Override public @NotNull SortTerm type() {
      return wellTyped.succ();
    }

    @Override public @NotNull Sort freezeHoles(@NotNull TyckState state) {
      return this;
    }
  }
}
