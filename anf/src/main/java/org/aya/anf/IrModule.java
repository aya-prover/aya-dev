// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.anf.ir.module.IRFunc;
import org.aya.anf.ir.module.IRFuncDesc;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.DataDef;
import org.jetbrains.annotations.NotNull;

/// Represents an entire compilation module.
public record IrModule(
  @NotNull ImmutableSeq<DataDef> data,
  @NotNull ImmutableSeq<ConDef> constructors,
  @NotNull ImmutableMap<IRFuncDesc, IRFunc> functions
) {

  public record Builder(
    @NotNull MutableList<DataDef> data,
    @NotNull MutableList<ConDef> constructors,
    @NotNull MutableMap<IRFuncDesc, IRFunc> functions
  ) {

    public Builder() {
      this(MutableList.create(), MutableList.create(), MutableMap.create());
    }

    public @NotNull IrModule build() {
      return new IrModule(data.toSeq(), constructors.toSeq(), ImmutableMap.from(functions));
    }
  }

  public @NotNull Doc render() {
    return null;
  }
}
