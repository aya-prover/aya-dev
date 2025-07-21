// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import org.aya.anf.ir.struct.IrVarDecl;
import org.aya.anf.ir.struct.LetClause;
import org.jetbrains.annotations.NotNull;

/// CPS-style expression builder for the IR.
public class ExpBuilder {
  private final @NotNull MutableLinkedHashMap<String, IrVarDecl> env;
  private final @NotNull MutableList<LetClause> expBinds = MutableList.create();

  public ExpBuilder(@NotNull MutableLinkedHashMap<String, IrVarDecl> ref) {
    env = MutableLinkedHashMap.from(ref);
  }

  public @NotNull IrVarDecl addBind() {
    return null;
  }
}
