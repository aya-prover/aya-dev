// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.anf.ir.module.IRFunc;
import org.aya.anf.ir.module.IRFuncDesc;
import org.aya.anf.misc.IrModuleData;
import org.aya.generic.Modifier;
import org.aya.resolve.ResolveInfo;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

public class ModuleCompiler {
  private final @NotNull ResolveInfo resolveInfo;
  private final @NotNull ImmutableSeq<TyckDef> defs;

  private final @NotNull IrModuleData ir = new IrModuleData();
  private final @NotNull MutableList<FnDef> funcs = MutableList.create();

  public ModuleCompiler(@NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<TyckDef> defs) {
    this.resolveInfo = resolveInfo;
    this.defs = defs;
  }

  public void compile() {
    resolveNonFunctionGlobals();
    createFunctionHints();
    compileFunctions();
  }

  /// Preprocesses all non-function global definitions, such as datatype and constructors.
  /// This should also set up global definition sites and expose them as part of compilation
  /// context so that during function compilation, the lowering process may refer to them
  /// (e.g., for inlining).
  private void resolveNonFunctionGlobals() {
    for (var def: defs) {
      switch (def) {
        case DataDef d -> ir.data.append(d);
        case ConDef c -> ir.constructors.append(c);
        case FnDef f -> funcs.append(f);
        default -> throw new Panic("compilation of " + def + " is not implemented yet");
      }
    }
  }

  private void createFunctionHints() {
    // TODO
  }

  private void compileFunctions() {
    for (var func: funcs) {
      var desc = new IRFuncDesc.Direct(func.ref().name());
      var attr = new IRFunc.FuncAttr(func.ref().concrete.modifiers.contains(Modifier.Tailrec));
      var generated = new IRFunc(attr, null); // TODO
      ir.functions.put(desc, generated);
    }
  }
}
