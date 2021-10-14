// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

import org.aya.api.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

public sealed interface Value permits FormValue, IntroValue, RefValue {
  record Param(@NotNull LocalVar ref, @NotNull Value type, boolean explicit) {}
}
