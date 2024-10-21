// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public non-sealed class PhysicalModuleContext implements ModuleContext {
  public final @NotNull Context parent;
  public final @NotNull ModuleExport2 exports = new ModuleExport2();
  public final @NotNull ModuleSymbol2<AnyVar> symbols = new ModuleSymbol2<>();
  public final @NotNull MutableMap<String, ModuleExport2> modules = MutableHashMap.create();
  private final @NotNull ModulePath modulePath;
  @Override public @NotNull ModulePath modulePath() { return modulePath; }

  private @Nullable NoExportContext exampleContext;

  public PhysicalModuleContext(@NotNull Context parent, @NotNull ModulePath modulePath) {
    this.parent = parent;
    this.modulePath = modulePath;
  }

  @Override public void importModule(
    @NotNull String modName,
    @NotNull ModuleExport2 modExport,
    @NotNull Stmt.Accessibility accessibility,
    boolean isDefined,
    @NotNull SourcePos sourcePos
  ) {
    ModuleContext.super.importModule(modName, modExport, accessibility, isDefined, sourcePos);
    if (accessibility == Stmt.Accessibility.Public) {
      exports.export(modName, modExport);
    }
  }

  @Override public boolean exportSymbol(@NotNull String name, @NotNull AnyDefVar ref) {
    return exports.export(name, ref);
  }

  public @NotNull NoExportContext exampleContext() {
    if (exampleContext == null) exampleContext = new NoExportContext(this);
    return exampleContext;
  }

  @Override public @NotNull Context parent() { return parent; }
  @Override public @NotNull ModuleSymbol2<AnyVar> symbols() { return symbols; }
  @Override public @NotNull MutableMap<String, ModuleExport2> modules() { return modules; }
  @Override public @NotNull ModuleExport2 exports() { return exports; }
}
