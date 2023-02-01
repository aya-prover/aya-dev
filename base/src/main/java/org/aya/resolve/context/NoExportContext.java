// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.ref.AnyVar;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author ice1000
 * Used for examples and counterexamples
 */
public record NoExportContext(
  @NotNull PhysicalModuleContext parent,
  @NotNull ModuleSymbol<AnyVar> symbols,
  @NotNull MutableMap<ModulePath.Qualified, ModuleExport> modules,
  @Override @NotNull ImmutableSeq<String> moduleName
) implements ModuleContext {
  public NoExportContext(
    @NotNull PhysicalModuleContext parent,
    @NotNull ModuleSymbol<AnyVar> symbols,
    @NotNull MutableMap<ModulePath.Qualified, ModuleExport> modules
  ) {
    this(parent, symbols, modules, parent.moduleName().appended(":NoExport"));
  }

  public NoExportContext(@NotNull PhysicalModuleContext parent) {
    this(parent, new ModuleSymbol<>(), MutableHashMap.create());
  }

  @Override public @NotNull Path underlyingFile() {
    return parent.underlyingFile();
  }

  @Override public @NotNull Map<ModulePath, ModuleExport> exports() {
    return Map.empty();
  }
}
