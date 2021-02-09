// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import org.glavo.kala.collection.Map;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.Reporter;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Var;
import org.mzi.concrete.Stmt;
import org.mzi.concrete.resolve.error.*;

import java.util.function.Function;

/**
 * @author re-xyr
 */
public final class ModuleContext implements Context {
  private final @NotNull Context parent;
  private final MutableMap<String, MutableMap<Seq<String>, Var>> globals = MutableHashMap.of();
  private final MutableMap<Seq<String>, MutableMap<String, Var>> modules = MutableHashMap.of();
  private final MutableMap<Seq<String>, MutableMap<String, Var>> exports = MutableHashMap.of();

  public ModuleContext(@NotNull Context parent) {
    this.parent = parent;
  }

  @Override
  public @NotNull Context getParent() {
    return parent;
  }

  @Override
  public @NotNull Reporter getReporter() {
    return parent.getReporter();
  }

  @Override
  public @Nullable Var getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    var result = globals.getOrNull(name);
    if (result == null) return null;
    else if (result.size() == 1) return result.iterator().next().getValue();
    else {
      var disamb = Buffer.<Seq<String>>of();
      result.forEach((k, v) -> disamb.append(k));
      getReporter().report(new AmbiguousNameError(name, disamb.toImmutableSeq(), sourcePos));
      throw new ContextException();
    }
  }

  @Override
  public @Nullable Var getQualifiedLocalMaybe(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var mod = modules.get(modName);
    if (mod == null) return null;
    var ref = mod.getOrNull(name);
    if (ref == null) {
      getReporter().report(new QualifiedNameNotFoundError(modName, name, sourcePos));
      throw new ContextException();
    }
    else return ref;
  }

  public @NotNull MutableMap<Seq<String>, MutableMap<String, Var>> export() {
    return exports;
  }

  public void importModule(
    @NotNull ImmutableSeq<String> modName,
    @NotNull Stmt.Accessibility accessibility,
    @NotNull MutableMap<Seq<String>, MutableMap<String, Var>> module,
    @NotNull SourcePos sourcePos
  ) {
    module.forEach((name, mod) -> {
      var componentName = modName.concat(name);
      if (modules.containsKey(modName.concat(name))) {
        getReporter().report(new DuplicateModNameError(modName, sourcePos));
        throw new ContextException();
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
    if (mod == null) {
      getReporter().report(new ModNameNotFoundError(modName, sourcePos));
      throw new ContextException();
    }
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
      if (getUnqualifiedMaybe(name, sourcePos) != null) {
        getReporter().report(new ShadowingWarn(name, sourcePos));
      }
      globals.set(name, MutableHashMap.of());
    } else {
      getReporter().report(new AmbiguousNameWarn(name, sourcePos));
    }
    globals.get(name).set(modName, ref);
    if (accessibility == Stmt.Accessibility.Public) {
      if (exports.get(TOP_LEVEL_MOD_NAME).containsKey(name)) {
        getReporter().report(new DuplicateExportError(name, sourcePos));
      }
      else exports.get(TOP_LEVEL_MOD_NAME).set(name, ref);
    }
  }
}
