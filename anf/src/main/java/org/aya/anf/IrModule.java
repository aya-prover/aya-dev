// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf;

import kala.collection.immutable.ImmutableSeq;
import org.aya.anf.ir.module.IRFunc;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.def.ConDef;
import org.aya.syntax.core.def.DataDef;
import org.aya.util.PrettierOptions;
import org.jetbrains.annotations.NotNull;

/// Represents an entire compilation module.
public record IrModule(
  @NotNull ImmutableSeq<DataDef> data,
  @NotNull ImmutableSeq<ConDef> constructors,
  @NotNull ImmutableSeq<IRFunc> functions
) implements AyaDocile {

  @Override
  public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return Doc.english("<IrModule>"); // TODO
  }
}
