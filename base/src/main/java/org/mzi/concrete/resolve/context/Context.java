// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.Reporter;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Var;
import org.mzi.concrete.resolve.error.QualifiedNameNotFoundError;
import org.mzi.concrete.resolve.error.ShadowingWarn;
import org.mzi.concrete.resolve.error.UnqualifiedNameNotFoundError;
import org.mzi.ref.LocalVar;

/**
 * @author re-xyr
 */
public interface Context {
  Seq<String> TOP_LEVEL_MOD_NAME = Seq.of();

  @Nullable Context getParent();

  @NotNull Reporter getReporter();

  @Nullable Var getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos);
  default @Nullable Var getUnqualifiedMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    var p = getParent();
    var ref = getUnqualifiedLocalMaybe(name, sourcePos);
    if (ref == null) {
      if (p == null) return null;
      else return p.getUnqualifiedMaybe(name, sourcePos);
    } else return ref;
  }
  default @NotNull Var getUnqualified(@NotNull String name, @NotNull SourcePos sourcePos) {
    var result = getUnqualifiedMaybe(name, sourcePos);
    if (result == null) {
      getReporter().report(new UnqualifiedNameNotFoundError(name, sourcePos));
      throw new ContextException();
    }
    return result;
  }

  @Nullable Var getQualifiedLocalMaybe(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos);
  default @Nullable Var getQualifiedMaybe(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var p = getParent();
    var ref = getQualifiedLocalMaybe(modName, name, sourcePos);
    if (ref == null) {
      if (p == null) return null;
      else return p.getQualifiedMaybe(modName, name, sourcePos);
    } else return ref;
  }
  default @NotNull Var getQualified(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var result = getQualifiedMaybe(modName, name, sourcePos);
    if (result == null) {
      getReporter().report(new QualifiedNameNotFoundError(modName, name, sourcePos));
      throw new ContextException();
    }
    return result;
  }

  @Nullable MutableMap<String, Var> getModuleLocalMaybe(@NotNull Seq<String> modName, @NotNull SourcePos sourcePos);
  default @Nullable MutableMap<String, Var> getModuleMaybe(@NotNull Seq<String> modName, @NotNull SourcePos sourcePos) {
    var p = getParent();
    var ref = getModuleLocalMaybe(modName, sourcePos);
    if (ref == null) {
      if (p == null) return null;
      else return p.getModuleMaybe(modName, sourcePos);
    } else return ref;
  }

  default @NotNull BindContext bind(@NotNull String name, @NotNull LocalVar ref, @NotNull SourcePos sourcePos) {
    if (getUnqualifiedMaybe(name, sourcePos) != null) {
      getReporter().report(new ShadowingWarn(name, sourcePos));
    }
    return new BindContext(this, name, ref);
  }

  default @NotNull ModuleContext derive() {
    return new ModuleContext(this);
  }

  class ContextException extends RuntimeException {
  }
}
