// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author ice1000
 * Used for examples and counterexamples
 */
public record NoExportContext(
  @NotNull PhysicalModuleContext parent,
  @NotNull MutableModuleSymbol<ContextUnit> symbols,
  @NotNull MutableMap<ModulePath.Qualified, ModuleExport> modules
) implements ModuleContext {
  @Override
  public @NotNull ImmutableSeq<String> moduleName() {
    return parent.moduleName().appended(":NoExport");
  }

  public NoExportContext(@NotNull PhysicalModuleContext parent) {
    this(parent, new MutableModuleSymbol<>(), MutableHashMap.create());
  }

  @Override public @NotNull Path underlyingFile() {
    return parent.underlyingFile();
  }

  @Override
  public void doExport(@NotNull ModulePath componentName, @NotNull String name, @NotNull DefVar<?, ?> ref, @NotNull SourcePos sourcePos) {
  }

  @Override
  public @NotNull Map<ModulePath, ModuleExport> exports() {
    return Map.empty();
  }
}
