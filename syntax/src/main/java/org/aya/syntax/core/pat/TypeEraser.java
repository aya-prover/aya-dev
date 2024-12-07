// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public class TypeEraser implements UnaryOperator<@NotNull Pat> {
  @Override public @NotNull Pat apply(@NotNull Pat pat) {
    return switch (pat) {
      case Pat.Bind _ -> Pat.Misc.UntypedBind;
      default -> pat.descentPat(this);
    };
  }

  public static @NotNull Pat erase(@NotNull Pat pat) {
    return new TypeEraser().apply(pat);
  }
}
