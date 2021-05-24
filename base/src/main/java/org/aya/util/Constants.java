// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.util;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface Constants {
  @NotNull @NonNls String ANONYMOUS_PREFIX = "_";
  @NotNull @NonNls String GENERATED_POSTFIX = "$";
  @NotNull @NonNls String SCOPE_SEPARATOR = "::";

  static @NotNull LocalVar anonymous() {
    return new LocalVar(ANONYMOUS_PREFIX, SourcePos.NONE);
  }
}
