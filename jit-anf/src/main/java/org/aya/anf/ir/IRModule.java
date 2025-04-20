// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir;

import kala.collection.mutable.MutableList;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.DataDef;
import org.jetbrains.annotations.NotNull;

/// Represents an entire compilation module.
public record IRModule(
  @NotNull MutableList<DataDef> data,
  @NotNull MutableList<ConDef> constructors,
  @NotNull MutableList<Object> functions
) {

  public @NotNull String debugRender() {
    var b = new StringBuilder();
    b.append("Data:\n");
    data.forEach(d -> b.append(d.easyToString()).append("\n"));
    b.append("Constructors:\n");
    constructors.forEach(d -> b.append(d.easyToString()).append("\n"));
    return b.toString();
  }
}
