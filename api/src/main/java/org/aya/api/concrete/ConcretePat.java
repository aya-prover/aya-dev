// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.concrete;

import org.aya.api.distill.AyaDocile;
import org.aya.api.error.SourcePos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface ConcretePat extends AyaDocile {
  @NotNull SourcePos sourcePos();
  boolean explicit();
}
