// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.context;

import kala.collection.Seq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.Stmt;
import org.aya.concrete.resolve.error.DuplicateExportError;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record PhysicalModuleContext(
  @NotNull Context parent,
  @NotNull MutableMap<String, MutableMap<Seq<String>, Var>> definitions,
  @NotNull MutableMap<Seq<String>, MutableMap<String, Var>> modules,
  @NotNull MutableMap<Seq<String>, MutableMap<String, Var>> exports
) implements ModuleContext {
  public PhysicalModuleContext(@NotNull Context parent) {
    this(parent,
      MutableHashMap.of(),
      MutableHashMap.of(TOP_LEVEL_MOD_NAME, MutableHashMap.of()),
      MutableHashMap.of(TOP_LEVEL_MOD_NAME, MutableHashMap.of())
    );
  }

  @Override public void importModule(
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos,
    Seq<String> componentName,
    MutableMap<String, Var> mod
  ) {
    ModuleContext.super.importModule(accessibility, sourcePos, componentName, mod);
    if (accessibility == Stmt.Accessibility.Public) exports.set(componentName, mod);
  }

  @Override public void addGlobal(
    @NotNull Seq<String> modName,
    @NotNull String name,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull Var ref,
    @NotNull SourcePos sourcePos
  ) {
    ModuleContext.super.addGlobal(modName, name, accessibility, ref, sourcePos);
    if (accessibility == Stmt.Accessibility.Public) {
      if (exports.get(TOP_LEVEL_MOD_NAME).containsKey(name)) {
        reportAndThrow(new DuplicateExportError(name, sourcePos));
      } else exports.get(TOP_LEVEL_MOD_NAME).set(name, ref);
    }
  }
}
