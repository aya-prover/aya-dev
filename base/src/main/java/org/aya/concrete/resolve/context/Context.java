// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.context;

import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.api.util.BreakingException;
import org.aya.concrete.resolve.error.QualifiedNameNotFoundError;
import org.aya.concrete.resolve.error.ShadowingWarn;
import org.aya.concrete.resolve.error.UnqualifiedNameNotFoundError;
import org.aya.ref.LocalVar;
import org.aya.util.Constants;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public interface Context {
  Seq<String> TOP_LEVEL_MOD_NAME = Seq.of();

  @Nullable Context parent();

  @NotNull Reporter reporter();

  @Contract("_->fail")
  default <T> @NotNull T reportAndThrow(@NotNull Problem problem) {
    reporter().report(problem);
    throw new ContextException();
  }

  @Nullable Var getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos);
  default @Nullable Var getUnqualifiedMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    var ref = getUnqualifiedLocalMaybe(name, sourcePos);
    if (ref == null) {
      var p = parent();
      if (p == null) return null;
      else return p.getUnqualifiedMaybe(name, sourcePos);
    } else return ref;
  }
  default @NotNull Var getUnqualified(@NotNull String name, @NotNull SourcePos sourcePos) {
    var result = getUnqualifiedMaybe(name, sourcePos);
    if (result == null) reportAndThrow(new UnqualifiedNameNotFoundError(name, sourcePos));
    return result;
  }

  @Nullable Var getQualifiedLocalMaybe(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos);
  default @Nullable Var getQualifiedMaybe(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var ref = getQualifiedLocalMaybe(modName, name, sourcePos);
    if (ref == null) {
      var p = parent();
      if (p == null) return null;
      else return p.getQualifiedMaybe(modName, name, sourcePos);
    } else return ref;
  }
  default @NotNull Var getQualified(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var result = getQualifiedMaybe(modName, name, sourcePos);
    if (result == null) reportAndThrow(new QualifiedNameNotFoundError(modName, name, sourcePos));
    return result;
  }

  @Nullable MutableMap<String, Var> getModuleLocalMaybe(@NotNull Seq<String> modName, @NotNull SourcePos sourcePos);
  default @Nullable MutableMap<String, Var> getModuleMaybe(@NotNull Seq<String> modName, @NotNull SourcePos sourcePos) {
    var ref = getModuleLocalMaybe(modName, sourcePos);
    if (ref == null) {
      var p = parent();
      if (p == null) return null;
      else return p.getModuleMaybe(modName, sourcePos);
    } else return ref;
  }

  default @NotNull BindContext bind(@NotNull String name, @NotNull LocalVar ref, @NotNull SourcePos sourcePos) {
    if (getUnqualifiedMaybe(name, sourcePos) != null && !name.startsWith(Constants.ANONYMOUS_PREFIX)) {
      reporter().report(new ShadowingWarn(name, sourcePos));
    }
    return new BindContext(this, name, ref);
  }

  default @NotNull ModuleContext derive() {
    return new ModuleContext(this);
  }

  class ContextException extends BreakingException {
    @Override public void printHint() {
      System.err.println("A reference error was discovered during resolving.");
    }

    @Override public int exitCode() {
      return 1;
    }
  }
}
