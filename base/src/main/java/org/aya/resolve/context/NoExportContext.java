// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.ModulePath;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Used for `let open`
 */
public record NoExportContext(
  @NotNull Context parent,
  @NotNull ModuleSymbol<AnyVar> symbols,
  @NotNull MutableMap<ModuleName.Qualified, ModuleExport> modules,
  @Override @NotNull ModulePath modulePath
) implements ModuleContext {
  public NoExportContext(
    @NotNull Context parent,
    @NotNull ModuleSymbol<AnyVar> symbols,
    @NotNull MutableMap<ModuleName.Qualified, ModuleExport> modules
  ) {
    this(parent, symbols, modules, parent.modulePath().derive(":NoExport"));
  }

  public NoExportContext(@NotNull Context parent) {
    this(parent, new ModuleSymbol<>(), MutableHashMap.create());
  }

  @Override public @NotNull Path underlyingFile() { return parent.underlyingFile(); }
  @Override public @NotNull ModuleExport exports() { return new ModuleExport(); }
}
