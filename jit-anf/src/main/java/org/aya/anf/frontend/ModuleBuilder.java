// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend;

import kala.collection.mutable.MutableList;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

public class ModuleBuilder {
  private final MutableList<DataDef> data = MutableList.create();
  private final MutableList<ConDef> constructors = MutableList.create();
  private final MutableList<Object> functions = MutableList.create();

  private void addTopLevelDef(@NotNull TyckDef def) {
    switch (def) {
      case ConDef conDef -> constructors.append(conDef);
      case DataDef dataDef -> data.append(dataDef);
      case FnDef fnDef -> {
        // TODO
      }
      default -> throw new Panic("unimplemented");
    }
  }
}
