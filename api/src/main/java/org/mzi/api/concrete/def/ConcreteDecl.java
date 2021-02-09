// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.concrete.def;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.core.def.CoreDef;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.DefVar;

public interface ConcreteDecl {
  @Contract(pure = true) @NotNull DefVar<? extends CoreDef, ? extends ConcreteDecl> ref();
  @Contract(pure = true) @NotNull SourcePos sourcePos();
}
