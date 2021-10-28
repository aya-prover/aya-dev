// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.mutable.MutableMap;
import org.aya.core.Meta;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record TyckState(
  @NotNull MutableMap<@NotNull Meta, @NotNull Term> metas
) {
  public TyckState() {
    this(MutableMap.create());
  }
}
