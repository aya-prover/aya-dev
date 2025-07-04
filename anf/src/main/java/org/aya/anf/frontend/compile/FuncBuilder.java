// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.mutable.MutableList;
import org.aya.anf.ir.struct.IRVarDecl;
import org.jetbrains.annotations.NotNull;

public class FuncBuilder {

  private final @NotNull MutableList<IRVarDecl.Generated> unnamedVars = MutableList.create();
}
