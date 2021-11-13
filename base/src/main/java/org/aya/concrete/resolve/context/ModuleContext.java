// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.resolve.context;

import kala.collection.Map;
import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.aya.api.error.Reporter;
import org.aya.util.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.resolve.error.*;
import org.aya.concrete.stmt.Stmt;
import org.aya.generic.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @author re-xyr
 */
public sealed interface ModuleContext extends Context permits NoExportContext, PhysicalModuleContext {
  @Override @NotNull Context parent();
  @Override default @NotNull Reporter reporter() {
    return parent().reporter();
  }

  @NotNull MutableMap<String, MutableMap<Seq<String>, Var>> definitions();
  @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, Var>> modules();

  @Override default @Nullable Var getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    var result = definitions().getOrNull(name);
    if (result == null) return null;
    else if (result.size() == 1) return result.iterator().next().getValue();
    else {
      var disamb = DynamicSeq.<Seq<String>>create();
      result.forEach((k, v) -> disamb.append(k));
      return reportAndThrow(new AmbiguousNameError(name, disamb.toImmutableSeq(), sourcePos));
    }
  }

  @Override default @Nullable Var
  getQualifiedLocalMaybe(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var mod = modules().getOrNull(modName);
    if (mod == null) return null;
    var ref = mod.getOrNull(name);
    if (ref == null) reportAndThrow(new QualifiedNameNotFoundError(modName, name, sourcePos));
    return ref;
  }

  @Override default @Nullable MutableMap<String, Var> getModuleLocalMaybe(@NotNull ImmutableSeq<String> modName) {
    return modules().getOrNull(modName);
  }

  default void importModules(
    @NotNull ImmutableSeq<String> modName,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull MutableMap<ImmutableSeq<String>, MutableMap<String, Var>> module,
    @NotNull SourcePos sourcePos
  ) {
    module.forEach((name, mod) -> importModule(accessibility, sourcePos, modName.concat(name), mod));
  }

  default void importModule(
    @NotNull Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos,
    ImmutableSeq<String> componentName,
    MutableMap<String, Var> mod
  ) {
    var modules = modules();
    if (modules.containsKey(componentName)) {
      reportAndThrow(new DuplicateModNameError(componentName, sourcePos));
    }
    if (getModuleMaybe(componentName) != null) {
      reporter().report(new ModShadowingWarn(componentName, sourcePos));
    }
    modules.set(componentName, mod);
  }

  default void openModule(
    @NotNull ImmutableSeq<String> modName,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull Function<String, Boolean> using,
    @NotNull Map<String, String> rename,
    @NotNull SourcePos sourcePos
  ) {
    var mod = getModuleMaybe(modName);
    if (mod == null) reportAndThrow(new ModNameNotFoundError(modName, sourcePos));
    mod.forEach((name, ref) -> {
      if (using.apply(name)) {
        var newName = rename.getOrDefault(name, name);
        addGlobal(modName, newName, accessibility, ref, sourcePos);
      }
    });
  }

  default void addGlobalSimple(@NotNull Stmt.Accessibility acc, @NotNull Var ref, @NotNull SourcePos sourcePos) {
    addGlobal(TOP_LEVEL_MOD_NAME, ref.name(), acc, ref, sourcePos);
  }

  default void addGlobal(
    @NotNull ImmutableSeq<String> modName,
    @NotNull String name,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull Var ref,
    @NotNull SourcePos sourcePos
  ) {
    var definitions = definitions();
    if (!definitions.containsKey(name)) {
      if (getUnqualifiedMaybe(name, sourcePos) != null && !name.startsWith(Constants.ANONYMOUS_PREFIX)) {
        reporter().report(new ShadowingWarn(name, sourcePos));
      }
      definitions.set(name, MutableHashMap.create());
    } else if (definitions.get(name).containsKey(modName)) {
      reportAndThrow(new DuplicateNameError(name, ref, sourcePos));
    } else {
      reporter().report(new AmbiguousNameWarn(name, sourcePos));
    }
    definitions.get(name).set(modName, ref);
    if (modName.equals(TOP_LEVEL_MOD_NAME)) {
      // Defined, not imported.
      modules().get(TOP_LEVEL_MOD_NAME).set(name, ref);
    }
  }
}
