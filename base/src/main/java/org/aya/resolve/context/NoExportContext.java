// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.ref.AnyVar;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author ice1000
 * Used for examples and counterexamples, also `let open`
 */
public record NoExportContext(
  @NotNull Context parent,
  @NotNull ModuleSymbol<AnyVar> symbols,
  @NotNull MutableMap<ModuleName.Qualified, ModuleExport> modules,
  @Override @NotNull ModulePath moduleName
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

  @Override public @NotNull Path underlyingFile() {
    return parent.underlyingFile();
  }

  @Override public @NotNull ModuleExport exports() {
    return new ModuleExport();
  }
}
