// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import org.aya.concrete.stmt.Decl;
import org.aya.ref.DefVar;
import org.aya.ref.GenericDefVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public sealed interface GenericDef permits ClassDef, Def {
  @NotNull GenericDefVar<?, ?> ref();
}
