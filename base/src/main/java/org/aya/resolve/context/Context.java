// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.function.CheckedFunction;
import org.aya.generic.InterruptException;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.tyck.tycker.Problematic;
import org.aya.util.Panic;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author re-xyr
 */
public interface Context extends Problematic {
  @Nullable Context parent();
  @NotNull Reporter reporter();
  @NotNull Path underlyingFile();

  default <T> @Nullable T iterate(@NotNull CheckedFunction<@NotNull Context, @Nullable T, ResolvingInterruptedException> f) throws ResolvingInterruptedException {
    var p = this;
    while (p != null) {
      var result = f.applyChecked(p);
      if (result != null) return result;
      p = p.parent();
    }
    return null;
  }

  /**
   * The path of this module
   */
  default @NotNull ModulePath modulePath() {
    var p = parent();
    assert p != null;
    return p.modulePath();
  }

  @Contract("_ -> fail")
  default <T> @NotNull T reportAndThrow(@NotNull Problem problem) throws ResolvingInterruptedException {
    fail(problem);
    throw new ResolvingInterruptedException();
  }

  @Contract("_ -> fail")
  default <T> @NotNull T reportAllAndThrow(@NotNull SeqLike<Problem> problems) throws ResolvingInterruptedException {
    reportAll(problems);
    throw new ResolvingInterruptedException();
  }

  default void reportAll(@NotNull SeqLike<Problem> problems) {
    problems.forEach(this::fail);
  }

  /**
   * Getting a symbol by name {@param name}.
   *
   * @param name an id which probably unqualified
   */
  default @NotNull AnyVar get(@NotNull QualifiedID name) throws ResolvingInterruptedException {
    return switch (name.component()) {
      case ModuleName.ThisRef _ -> getUnqualified(name.name(), name.sourcePos());
      case ModuleName.Qualified qualified -> getQualified(qualified, name.name(), name.sourcePos());
    };
  }

  /**
   * @see Context#get(QualifiedID)
   */
  default @Nullable AnyVar getMaybe(@NotNull QualifiedID name) throws ResolvingInterruptedException {
    return switch (name.component()) {
      case ModuleName.ThisRef _ -> getUnqualifiedMaybe(name.name(), name.sourcePos());
      case ModuleName.Qualified qualified -> getQualifiedMaybe(qualified, name.name(), name.sourcePos());
    };
  }

  default MutableList<LocalVar> collect(@NotNull MutableList<LocalVar> container) {
    return container;
  }

  /// @return all symbols with name {@param name}
  /// @implSpec return null if not found
  @Nullable Candidate<AnyVar> getCandidateLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos);

  default @Nullable Candidate<AnyVar> getCandidateMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    try {
      return iterate(c -> {
        var candy = c.getCandidateLocalMaybe(name, sourcePos);
        return candy == null || candy.isEmpty() ? null : candy;
      });
    } catch (ResolvingInterruptedException e) {
      return Panic.unreachable();
    }
  }

  /**
   * Trying to get a symbol by unqualified name {@param name} in {@code this} context.
   */
  default @Nullable AnyVar getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) throws ResolvingInterruptedException {
    var candy = getCandidateLocalMaybe(name, sourcePos);
    if (candy == null) return null;
    assert !candy.isEmpty() : "incorrect implementation";
    if (candy.isAmbiguous()) {
      reportAndThrow(new NameProblem.AmbiguousNameError(
        name, candy.from(), sourcePos));
    }

    return candy.get();
  }

  /**
   * Trying to get a symbol which can referred by unqualified name {@param name} in the whole context.
   *
   * @param name      the unqualified name
   * @param sourcePos the source pos for error reporting
   * @return null if not found
   * @see Context#getUnqualifiedLocalMaybe(String, SourcePos)
   */
  default @Nullable AnyVar getUnqualifiedMaybe(@NotNull String name, @NotNull SourcePos sourcePos) throws ResolvingInterruptedException {
    return iterate(c -> c.getUnqualifiedLocalMaybe(name, sourcePos));
  }

  /**
   * @see Context#getUnqualified(String, SourcePos)
   */
  default @NotNull AnyVar getUnqualified(@NotNull String name, @NotNull SourcePos sourcePos) throws ResolvingInterruptedException {
    var result = getUnqualifiedMaybe(name, sourcePos);
    if (result == null) reportAndThrow(new NameProblem.UnqualifiedNameNotFoundError(this, name, sourcePos));
    return result;
  }

  /**
   * Trying to get a symbol by qualified id {@code {modName}::{name}} in {@code this} context
   *
   * @return a symbol in component {@param modName}, even it is {@link ModuleName#This}; null if not found
   */
  @Nullable AnyVar getQualifiedLocalMaybe(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos
  ) throws ResolvingInterruptedException;

  /**
   * Trying to get a symbol by qualified id {@code {modName}::{name}} in the whole context with {@param accessibility}.
   *
   * @see Context#getQualifiedLocalMaybe(ModuleName.Qualified, String, SourcePos)
   */
  default @Nullable AnyVar getQualifiedMaybe(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos
  ) throws ResolvingInterruptedException {
    return iterate(c -> c.getQualifiedLocalMaybe(modName, name, sourcePos));
  }

  /**
   * @see Context#getQualifiedMaybe(ModuleName.Qualified, String, SourcePos)
   */
  default @NotNull AnyVar getQualified(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos
  ) throws ResolvingInterruptedException {
    var result = getQualifiedMaybe(modName, name, sourcePos);
    if (result == null)
      reportAndThrow(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
    return result;
  }

  /**
   * Trying to get a {@link ModuleExport} by a module {@param modName} in {@code this} context.
   *
   * @param modName qualified module name
   * @return a ModuleExport of that module; null if no such module.
   */
  @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModuleName.Qualified modName);

  /**
   * Trying to get a {@link ModuleExport} by a module {@param modName} in the whole context.
   *
   * @param modName qualified module name
   * @return a ModuleExport of that module; null if no such module.
   */
  default @Nullable ModuleExport getModuleMaybe(@NotNull ModuleName.Qualified modName) {
    try {
      return iterate(c -> c.getModuleLocalMaybe(modName));
    } catch (ResolvingInterruptedException e) {
      return Panic.unreachable();
    }
  }

  default @NotNull Context bind(@NotNull LocalVar ref, @NotNull Predicate<@Nullable Candidate<AnyVar>> toWarn) {
    return bind(ref.name(), ref, toWarn);
  }

  default @NotNull Context bind(@NotNull LocalVar ref) {
    return bind(ref, var -> var instanceof Candidate.Defined<AnyVar> defined
      && defined.get() instanceof LocalVar);
  }

  default @NotNull Context bind(
    @NotNull String name, @NotNull LocalVar ref,
    @NotNull Predicate<@Nullable Candidate<AnyVar>> toWarn
  ) {
    // do not bind ignored var, and users should not try to use it
    if (ref == LocalVar.IGNORED) return this;
    var exists = getCandidateMaybe(name, ref.definition());
    if (toWarn.test(exists) && (!(ref.generateKind() == GenerateKind.Basic.Anonymous))) {
      fail(new NameProblem.ShadowingWarn(name, ref.definition()));
    }
    return new BindContext(this, name, ref);
  }

  @ApiStatus.NonExtendable
  default @NotNull PhysicalModuleContext derive(@NotNull String extraName) {
    return derive(extraName, reporter());
  }

  default @NotNull PhysicalModuleContext derive(@NotNull String extraName, @NotNull Reporter reporter) {
    return derive(new ModulePath(ImmutableSeq.of(extraName)), reporter);
  }

  @ApiStatus.NonExtendable
  default @NotNull PhysicalModuleContext derive(@NotNull ModulePath extraName) {
    return derive(extraName, reporter());
  }

  default @NotNull PhysicalModuleContext derive(@NotNull ModulePath extraName, @NotNull Reporter reporter) {
    return new PhysicalModuleContext(reporter, this, modulePath().derive(extraName));
  }

  class ResolvingInterruptedException extends Exception {
    public InterruptException.InterruptStage stage() { return InterruptException.InterruptStage.Resolving; }
  }

  final class ResolveModuleInterruptedException extends InterruptException {
    @Override public InterruptStage stage() {
      return InterruptStage.Resolving;
    }
  }
}
