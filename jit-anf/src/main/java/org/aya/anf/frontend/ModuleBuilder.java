// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.anf.ir.IRFuncDesc;
import org.aya.anf.ir.IRModule;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

public class ModuleBuilder {
  private final MutableList<DataDef> data = MutableList.create();
  private final MutableList<ConDef> constructors = MutableList.create();
  private final MutableMap<IRFuncDesc, Object> functions = MutableMap.create();

  public void addTopLevelDef(@NotNull TyckDef def) {
    switch (def) {
      case ConDef conDef -> constructors.append(conDef);
      case DataDef dataDef -> data.append(dataDef);
      case FnDef fnDef -> {
        // TODO
      }
      default -> throw new Panic("unimplemented");
    }
  }

  public @NotNull IRModule build() {
    return new IRModule(data.toImmutableArray(), constructors.toImmutableArray(), ImmutableMap.from(functions));
  }

  public static @NotNull IRModule build(@NotNull SeqView<TyckDef> defs) {
    var builder = new ModuleBuilder();
    defs.forEach(builder::addTopLevelDef);
    return builder.build();
  }
}
