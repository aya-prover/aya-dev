// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.concrete.def;

import org.aya.api.core.def.CoreDef;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface ConcreteDecl {
  @Contract(pure = true) @NotNull DefVar<? extends CoreDef, ? extends ConcreteDecl> ref();
  @Contract(pure = true) @NotNull SourcePos sourcePos();
}
