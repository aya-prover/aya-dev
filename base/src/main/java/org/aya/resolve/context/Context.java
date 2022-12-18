// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.generic.Constants;
import org.aya.generic.util.InterruptException;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.aya.resolve.error.NameProblem;
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

  default <T> @NotNull T reportAllAndThrow(@NotNull SeqLike<Problem> problems) {
    reportAll(problems);
    throw new ResolvingInterruptedException();
  }

  default void reportAll(@NotNull SeqLike<Problem> problems) {
    problems.forEach(x -> reporter().report(x));
  }

  default @NotNull AnyVar get(@NotNull QualifiedID name) {
    return name.isUnqualified()
      ? getUnqualified(name.justName(), name.sourcePos())
      : getQualified(name, name.sourcePos());
  }

  default @Nullable AnyVar getMaybe(@NotNull QualifiedID name) {
    return name.isUnqualified()
      ? getUnqualifiedMaybe(name.justName(), name.sourcePos())
      : getQualifiedMaybe(name, name.sourcePos());
  }

  default MutableList<LocalVar> collect(@NotNull MutableList<LocalVar> container) {
    return container;
  }

  @Nullable AnyVar getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos);

  default @Nullable AnyVar getUnqualifiedMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    return iterate(c -> c.getUnqualifiedLocalMaybe(name, sourcePos));
  }

  default @NotNull AnyVar getUnqualified(@NotNull String name, @NotNull SourcePos sourcePos) {
    var result = getUnqualifiedMaybe(name, sourcePos);
    if (result == null) reportAndThrow(new NameProblem.UnqualifiedNameNotFoundError(this, name, sourcePos));
    return result;
  }

  @Nullable AnyVar getQualifiedLocalMaybe(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos);

  default @Nullable AnyVar getQualifiedMaybe(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    return iterate(c -> c.getQualifiedLocalMaybe(modName, name, sourcePos));
  }

  default @Nullable AnyVar getQualifiedMaybe(@NotNull QualifiedID qualifiedID, @NotNull SourcePos sourcePos) {
    var view = qualifiedID.ids().view();
    return getQualifiedMaybe(view.dropLast(1).toImmutableSeq(), view.last(), sourcePos);
  }

  default @NotNull AnyVar getQualified(@NotNull ImmutableSeq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    var result = getQualifiedMaybe(modName, name, sourcePos);
    if (result == null) reportAndThrow(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
    return result;
  }

  default @NotNull AnyVar getQualified(@NotNull QualifiedID qualifiedID, @NotNull SourcePos sourcePos) {
    var view = qualifiedID.ids().view();
    return getQualified(view.dropLast(1).toImmutableSeq(), view.last(), sourcePos);
  }

  /**
   * Trying to get a {@link ModuleExport} of module {@param modName} locally.
   *
   * @param modName qualified module name
   * @return the context of that module; null if no such module.
   */
  @Nullable ModuleExport getModuleLocalMaybe(@NotNull ImmutableSeq<String> modName);

  /**
   * Trying to get a {@link ModuleExport} of module {@param modName}.
   *
   * @param modName qualified module name
   * @return the context of that module; null if no such module.
   */
  default @Nullable ModuleExport getModuleMaybe(@NotNull ImmutableSeq<String> modName) {
    return iterate(c -> c.getModuleLocalMaybe(modName));
  }

  default @NotNull Context bind(
    @NotNull LocalVar ref,
    @NotNull SourcePos sourcePos,
    @NotNull Predicate<@Nullable AnyVar> toWarn
  ) {
    return bind(ref.name(), ref, sourcePos, toWarn);
  }

  default @NotNull Context bind(@NotNull LocalVar ref, @NotNull SourcePos sourcePos) {
    return bind(ref.name(), ref, sourcePos, var -> var instanceof LocalVar);
  }

  default @NotNull Context bind(
    @NotNull String name,
    @NotNull LocalVar ref,
    @NotNull SourcePos sourcePos,
    @NotNull Predicate<@Nullable AnyVar> toWarn
  ) {
    // do not bind ignored var, and users should not try to use it
    if (ref == LocalVar.IGNORED) return this;
    if (toWarn.test(getUnqualifiedMaybe(name, sourcePos)) && !name.startsWith(Constants.ANONYMOUS_PREFIX)) {
      reporter().report(new NameProblem.ShadowingWarn(name, sourcePos));
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
