// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.api.ref.LocalVar;
import org.aya.util.error.Global;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface Constants {
  @NotNull @NonNls String ANONYMOUS_PREFIX = "_";
  @NotNull @NonNls String GENERATED_POSTFIX = "'";
  @NotNull @NonNls String SCOPE_SEPARATOR = "::";
  @NotNull @NonNls String AYA_POSTFIX = ".aya";
  @NotNull @NonNls String AYAC_POSTFIX = ".ayac";

  static @NotNull LocalVar anonymous() {
    return new LocalVar(ANONYMOUS_PREFIX, SourcePos.NONE);
  }
  static @NotNull LocalVar randomlyNamed(@NotNull SourcePos pos) {
    return new LocalVar(randomName(pos), pos);
  }
  static @NotNull String randomName(@NotNull Object pos) {
    if (Global.NO_RANDOM_NAME) return ANONYMOUS_PREFIX;
    return ANONYMOUS_PREFIX + Math.abs(pos.hashCode()) % 10;
  }
}
