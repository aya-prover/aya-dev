// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public sealed interface GenericDef permits ClassDef, Def {
  @NotNull DefVar<?, ?> ref();

  @NotNull Term result();
}
