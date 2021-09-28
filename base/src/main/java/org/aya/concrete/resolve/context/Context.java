// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.context;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableMap;
import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.InterruptException;
import org.aya.concrete.resolve.error.QualifiedNameNotFoundError;
import org.aya.concrete.resolve.error.ShadowingWarn;
import org.aya.concrete.resolve.error.UnqualifiedNameNotFoundError;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.util.Constants;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * @author re-xyr
 */
public interface Context {
  ImmutableSeq<String> TOP_LEVEL_MOD_NAME = ImmutableSeq.empty();

  @Nullable Context parent();

  @NotNull Reporter reporter();

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
    var isUnqualified = name.isUnqualified();
    return isUnqualified
      ? getUnqualified(name.justName(), name.sourcePos())
      : getQualified(name, name.sourcePos());
  }

  default Buffer<LocalVar> collect(@NotNull Buffer<LocalVar> container) {
    return container;
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

  @Nullable Var getQualifiedLocalMaybe(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos);
  default @Nullable Var getQualifiedMaybe(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var ref = getQualifiedLocalMaybe(modName, name, sourcePos);
    if (ref == null) {
      var p = parent();
      if (p == null) return null;
      else return p.getQualifiedMaybe(modName, name, sourcePos);
    } else return ref;
  }
  default @NotNull Var getQualified(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var result = getQualifiedMaybe(modName, name, sourcePos);
    if (result == null) reportAndThrow(new QualifiedNameNotFoundError(modName, name, sourcePos));
    return result;
  }

  default @NotNull Var getQualified(@NotNull QualifiedID qualifiedID, @NotNull SourcePos sourcePos) {
    var view = qualifiedID.ids().view();
    var name = view.last();
    var modName = view.dropLast(1).toImmutableSeq();
    return getQualified(modName, name, sourcePos);
  }

  @Nullable MutableMap<String, Var> getModuleLocalMaybe(@NotNull ImmutableSeq<String> modName, @NotNull SourcePos sourcePos);
  default @Nullable MutableMap<String, Var> getModuleMaybe(@NotNull ImmutableSeq<String> modName, @NotNull SourcePos sourcePos) {
    var ref = getModuleLocalMaybe(modName, sourcePos);
    if (ref == null) {
      var p = parent();
      if (p == null) return null;
      else return p.getModuleMaybe(modName, sourcePos);
    } else return ref;
  }

  default @NotNull BindContext bind(
    @NotNull LocalVar ref,
    @NotNull SourcePos sourcePos,
    @NotNull Predicate<@Nullable Var> toWarn
  ) {
    return bind(ref.name(), ref, sourcePos, toWarn);
  }

  default @NotNull BindContext bind(
    @NotNull LocalVar ref,
    @NotNull SourcePos sourcePos
  ) {
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
