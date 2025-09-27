// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.misc;

import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.anf.ir.module.IRFunc;
import org.aya.anf.ir.module.IRFuncDesc;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.DataDef;
import org.jetbrains.annotations.NotNull;

/// Builder for `IrModule`. This is the module representation used after compilation
/// during the optimization phase. In the end it is serialized into an `IrModule`.
public class IrModuleData {
  public @NotNull MutableList<DataDef> data = MutableList.create();
  public @NotNull MutableList<ConDef> constructors = MutableList.create();
  public @NotNull MutableMap<IRFuncDesc, IRFunc> functions = MutableMap.create();
}
