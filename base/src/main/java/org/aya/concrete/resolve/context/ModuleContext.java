// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.context;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.Stmt;
import org.aya.concrete.resolve.error.*;
import org.aya.util.Constants;
import kala.collection.Map;
import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @author re-xyr
 */
public record ModuleContext(
  @NotNull Context parent,
  @NotNull MutableMap<String, MutableMap<Seq<String>, Var>> globals,
  @NotNull MutableMap<Seq<String>, MutableMap<String, Var>> modules,
  @NotNull MutableMap<Seq<String>, MutableMap<String, Var>> exports
) implements Context {
  public ModuleContext(@NotNull Context parent) {
    this(parent,
      MutableHashMap.of(),
      MutableHashMap.of(TOP_LEVEL_MOD_NAME, MutableHashMap.of()),
      MutableHashMap.of(TOP_LEVEL_MOD_NAME, MutableHashMap.of())
    );
  }

  @Override public @NotNull Reporter reporter() {
    return parent.reporter();
  }

  @Override public @Nullable Var getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    var result = globals.getOrNull(name);
    if (result == null) return null;
    else if (result.size() == 1) return result.iterator().next().getValue();
    else {
      var disamb = Buffer.<Seq<String>>of();
      result.forEach((k, v) -> disamb.append(k));
      return reportAndThrow(new AmbiguousNameError(name, disamb.toImmutableSeq(), sourcePos));
    }
  }

  @Override
  public @Nullable Var getQualifiedLocalMaybe(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var mod = modules.getOrNull(modName);
    if (mod == null) return null;
    var ref = mod.getOrNull(name);
    if (ref == null) reportAndThrow(new QualifiedNameNotFoundError(modName, name, sourcePos));
    return ref;
  }

  @Override
  public @Nullable MutableMap<String, Var> getModuleLocalMaybe(@NotNull Seq<String> modName, @NotNull SourcePos sourcePos) {
    return modules.getOrNull(modName);
  }

  public void importModule(
    @NotNull ImmutableSeq<String> modName,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull MutableMap<Seq<String>, MutableMap<String, Var>> module,
    @NotNull SourcePos sourcePos
  ) {
    module.forEach((name, mod) -> {
      var componentName = modName.concat(name);
      if (modules.containsKey(componentName)) {
        reportAndThrow(new DuplicateModNameError(modName, sourcePos));
      }
      if (getModuleMaybe(componentName, sourcePos) != null) {
        reporter().report(new ModShadowingWarn(componentName, sourcePos));
      }
      modules.set(componentName, mod);
      if (accessibility == Stmt.Accessibility.Public) exports.set(componentName, mod);
    });
  }

  public void openModule(
    @NotNull Seq<String> modName,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull Function<String, Boolean> using,
    @NotNull Map<String, String> rename,
    @NotNull SourcePos sourcePos
  ) {
    var mod = modules.getOrNull(modName);
    if (mod == null) reportAndThrow(new ModNameNotFoundError(modName, sourcePos));
    mod.forEach((name, ref) -> {
      if (using.apply(name)) {
        var newName = rename.getOrDefault(name, name);
        addGlobal(modName, newName, accessibility, ref, sourcePos);
      }
    });
  }

  public void addGlobal(
    @NotNull Seq<String> modName,
    @NotNull String name,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull Var ref,
    @NotNull SourcePos sourcePos
  ) {
    if (!globals.containsKey(name)) {
      if (getUnqualifiedMaybe(name, sourcePos) != null && !name.startsWith(Constants.ANONYMOUS_PREFIX)) {
        reporter().report(new ShadowingWarn(name, sourcePos));
      }
      globals.set(name, MutableHashMap.of());
    } else if (globals.get(name).containsKey(modName)) {
      reportAndThrow(new DuplicateNameError(name, ref, sourcePos));
    } else {
      reporter().report(new AmbiguousNameWarn(name, sourcePos));
    }
    globals.get(name).set(modName, ref);
    if (modName.equals(TOP_LEVEL_MOD_NAME)) {
      // Defined, not imported.
      modules.get(TOP_LEVEL_MOD_NAME).set(name, ref);
    }
    if (accessibility == Stmt.Accessibility.Public) {
      if (exports.get(TOP_LEVEL_MOD_NAME).containsKey(name)) {
        reportAndThrow(new DuplicateExportError(name, sourcePos));
      } else exports.get(TOP_LEVEL_MOD_NAME).set(name, ref);
    }
  }
}
