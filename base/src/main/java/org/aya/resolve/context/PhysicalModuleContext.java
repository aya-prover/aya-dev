// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.context.ModuleExport;
import org.aya.syntax.context.ModuleSymbol;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.QPath;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For: file modules, inductive/classes/etc. as modules
 *
 * @author re-xyr
 */
public class PhysicalModuleContext implements ModuleContext {
  public final @NotNull Context parent;
  public final @NotNull ModuleExport exports;
  public final @NotNull ModuleSymbol<AnyVar> symbols = new ModuleSymbol<>();
  public final @NotNull MutableMap<ModuleName.Qualified, ModuleExport> modules = MutableHashMap.create();
  private final @NotNull QPath qualifiedPath;
  @Override public @NotNull QPath qualifiedPath() { return qualifiedPath; }

  private @Nullable NoExportContext exampleContext;

  public PhysicalModuleContext(@NotNull Context parent, @NotNull QPath qualifiedPath) {
    this.parent = parent;
    this.qualifiedPath = qualifiedPath;
    this.exports = new ModuleExport(qualifiedPath);
  }

  @Override public boolean importModule(
    @NotNull ModuleName.Qualified modName,
    @NotNull ModuleExport modExport,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  ) {
    var ret = ModuleContext.super.importModule(modName, modExport, accessibility, sourcePos, reporter);
    if (accessibility == Stmt.Accessibility.Public) {
      exports.export(modName, modExport);
    }
    return ret;
  }

  @Override public boolean exportSymbol(@NotNull String name, @NotNull AnyDefVar ref) {
    return exports.export(name, ref);
  }

  public @NotNull NoExportContext exampleContext() {
    if (exampleContext == null) exampleContext = new NoExportContext(this);
    return exampleContext;
  }

  @Override public @NotNull Context parent() { return parent; }
  @Override public @NotNull ModuleSymbol<AnyVar> symbols() { return symbols; }
  @Override public @NotNull MutableMap<ModuleName.Qualified, ModuleExport> modules() { return modules; }
  @Override public @NotNull ModuleExport exports() { return exports; }
}
