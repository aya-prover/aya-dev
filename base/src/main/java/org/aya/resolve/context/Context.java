// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.generic.Constants;
import org.aya.generic.util.InterruptException;
import org.aya.ref.LocalVar;
import org.aya.ref.Var;
import org.aya.resolve.error.QualifiedNameNotFoundError;
import org.aya.resolve.error.ShadowingWarn;
import org.aya.resolve.error.UnqualifiedNameNotFoundError;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author re-xyr
 */
public interface Context {
  ImmutableSeq<String> TOP_LEVEL_MOD_NAME = ImmutableSeq.empty();

  @Nullable Context parent();
  @NotNull Reporter reporter();
  @NotNull Path underlyingFile();

  default <T> @Nullable T iterate(@NotNull Function<@NotNull Context, @Nullable T> f) {
    var p = this;
    while (p != null) {
      var result = f.apply(p);
      if (result != null) return result;
      p = p.parent();
    }
    return null;
  }

  default @NotNull ImmutableSeq<String> moduleName() {
    var p = parent();
    if (p == null) return ImmutableSeq.empty();
    else return p.moduleName();
  }

  @Contract("_->fail") default <T> @NotNull T reportAndThrow(@NotNull Problem problem) {
    reporter().report(problem);
    throw new ResolvingInterruptedException();
  }

  default @NotNull Var get(@NotNull QualifiedID name) {
    return name.isUnqualified()
      ? getUnqualified(name.justName(), name.sourcePos())
      : getQualified(name, name.sourcePos());
  }

  default DynamicSeq<LocalVar> collect(@NotNull DynamicSeq<LocalVar> container) {
    return container;
  }

  @Nullable Var getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos);
  default @Nullable Var getUnqualifiedMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    return iterate(c -> c.getUnqualifiedLocalMaybe(name, sourcePos));
  }
  default @NotNull Var getUnqualified(@NotNull String name, @NotNull SourcePos sourcePos) {
    var result = getUnqualifiedMaybe(name, sourcePos);
    if (result == null) reportAndThrow(new UnqualifiedNameNotFoundError(name, sourcePos));
    return result;
  }

  @Nullable Var getQualifiedLocalMaybe(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos);
  default @Nullable Var getQualifiedMaybe(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    return iterate(c -> c.getQualifiedLocalMaybe(modName, name, sourcePos));
  }
  default @NotNull Var getQualified(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var result = getQualifiedMaybe(modName, name, sourcePos);
    if (result == null) reportAndThrow(new QualifiedNameNotFoundError(modName, name, sourcePos));
    return result;
  }

  default @NotNull Var getQualified(@NotNull QualifiedID qualifiedID, @NotNull SourcePos sourcePos) {
    var view = qualifiedID.ids().view();
    return getQualified(view.dropLast(1).toImmutableSeq(), view.last(), sourcePos);
  }

  @Nullable MutableMap<String, Var> getModuleLocalMaybe(@NotNull ImmutableSeq<String> modName);
  default @Nullable MutableMap<String, Var> getModuleMaybe(@NotNull ImmutableSeq<String> modName) {
    return iterate(c -> c.getModuleLocalMaybe(modName));
  }

  default @NotNull BindContext bind(
    @NotNull LocalVar ref,
    @NotNull SourcePos sourcePos,
    @NotNull Predicate<@Nullable Var> toWarn
  ) {
    return bind(ref.name(), ref, sourcePos, toWarn);
  }

  default @NotNull BindContext bind(@NotNull LocalVar ref, @NotNull SourcePos sourcePos) {
    return bind(ref.name(), ref, sourcePos, var -> var instanceof LocalVar);
  }

  default @NotNull BindContext bind(
    @NotNull String name,
    @NotNull LocalVar ref,
    @NotNull SourcePos sourcePos,
    @NotNull Predicate<@Nullable Var> toWarn
  ) {
    if (toWarn.test(getUnqualifiedMaybe(name, sourcePos)) && !name.startsWith(Constants.ANONYMOUS_PREFIX)) {
      reporter().report(new ShadowingWarn(name, sourcePos));
    }
    return new BindContext(this, name, ref);
  }

  default @NotNull PhysicalModuleContext derive(@NotNull String extraName) {
    return new PhysicalModuleContext(this, this.moduleName().appended(extraName));
  }

  default @NotNull PhysicalModuleContext derive(@NotNull Seq<@NotNull String> extraName) {
    return new PhysicalModuleContext(this, this.moduleName().concat(extraName));
  }

  class ResolvingInterruptedException extends InterruptException {
    @Override public InterruptStage stage() {
      return InterruptStage.Resolving;
    }
  }
}
