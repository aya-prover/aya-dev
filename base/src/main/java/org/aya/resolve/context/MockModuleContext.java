// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple2;
import org.aya.concrete.stmt.Stmt;
import org.aya.ref.DefVar;
import org.aya.resolve.error.NameProblem;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A (Mock Module) Context is a weaker module context than {@link ModuleContext},
 * which is not able to importing, every definition are exported automatically, also there is no any submodule.
 */
public record MockModuleContext(
  @Override @NotNull Context parent,
  @NotNull ContextUnit.Exportable modVar,
  @NotNull MutableMap<String, DefVar<?, ?>> children,
  @NotNull MutableModuleExport thisExports
) implements ModuleLikeContext {
  public MockModuleContext(@NotNull Context parent, @NotNull ContextUnit.Exportable modVar) {
    this(parent, modVar, MutableMap.create(), new MutableModuleExport());
  }

  @Override
  public @NotNull ImmutableSeq<String> moduleName() {
    return parent().moduleName().appended(modVar().data().name());
  }

  @Override
  public @NotNull ModuleSymbol<ContextUnit.TopLevel> symbols() {
    return new MutableModuleSymbol<>(children().toImmutableSeq().collect(
      MutableMap.collector(
        Tuple2::component1,
        x -> MutableMap.<ModulePath, ContextUnit.TopLevel>of(
          ModulePath.This,
          ContextUnit.ofPublic(x.component2())))
    ));
  }

  @Override
  public @NotNull Map<ModulePath, ModuleExport> modules() {
    return ImmutableMap.of(ModulePath.This, thisExports);
  }

  @Override
  public @Nullable ContextUnit getUnqualifiedLocalMaybe(
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    if (modVar().data().name().equals(name)) return modVar();
    return children().getOption(name)
      .map(ContextUnit::ofPublic)
      .getOrNull();
  }

  @Override
  public @Nullable ContextUnit getQualifiedLocalMaybe(
    @NotNull ModulePath modName,
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    if (modName == ModulePath.This) {
      return getUnqualifiedLocalMaybe(name, accessibility, sourcePos);
    }

    return null;
  }

  public void define(@NotNull DefVar<?, ?> defVar, @NotNull SourcePos sourcePos) {
    var symbols = children();
    if (symbols.containsKey(defVar.name())) {
      reportAndThrow(new NameProblem.DuplicateNameError(defVar.name(), defVar, sourcePos));
    }

    symbols.put(defVar.name(), defVar);
    thisExports().export(ModulePath.This, defVar.name(), defVar);
  }

  @Override
  public @NotNull Map<ModulePath, ModuleExport> exports() {
    return Map.of(ModulePath.This, thisExports());
  }
}
