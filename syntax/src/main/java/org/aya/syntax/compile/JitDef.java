// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import org.aya.generic.AyaDocile;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.telescope.JitTele;
import org.aya.util.binop.Assoc;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A well-typed compiled definition
 *
 * @implNote every definition should be annotated by {@link CompiledAya}
 */
public abstract sealed class JitDef extends JitUnit implements AnyDef, AyaDocile permits JitClass, JitTele {
  @Override public @NotNull ModulePath fileModule() {
    return new ModulePath(module().module().take(metadata().fileModuleSize()));
  }

  @Override public @Nullable Assoc assoc() {
    var idx = metadata().assoc();
    if (idx == -1) return null;
    return Assoc.values()[idx];
  }

  @Override public @Nullable OpInfo opInfo() {
    var assoc = assoc();
    if (assoc == null) return null;
    return new OpInfo(name(), assoc);
  }

  @Override public abstract @NotNull JitTele signature();

  @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).def(this);
  }
}
