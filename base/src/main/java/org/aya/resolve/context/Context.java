// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Seq;
import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.concrete.stmt.Stmt;
import org.aya.generic.Constants;
import org.aya.generic.util.InterruptException;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.resolve.error.AccessibilityError;
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

  /**
   * The qualified module name of this module, should be absolute, not empty for non EmptyContext.
   */
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

  default @NotNull ContextUnit get(@NotNull QualifiedID name) {
    return get(name, null);
  }

  default @NotNull ContextUnit get(@NotNull QualifiedID name, @Nullable Stmt.Accessibility accessibility) {
    return name.isUnqualified()
      ? getUnqualified(name.justName(), accessibility, name.sourcePos())
      : getQualified(name, accessibility, name.sourcePos());
  }

  default @Nullable ContextUnit getMaybe(@NotNull QualifiedID name) {
    return getMaybe(name, null);
  }

  /**
   * Trying to obtain a symbol by {@param name}
   *
   * @return null if failed
   */
  default @Nullable ContextUnit getMaybe(@NotNull QualifiedID name, @Nullable Stmt.Accessibility accessibility) {
    return name.isUnqualified()
      ? getUnqualifiedMaybe(name.justName(), accessibility, name.sourcePos())
      : getQualifiedMaybe(name, accessibility, name.sourcePos());
  }

  default MutableList<LocalVar> collect(@NotNull MutableList<LocalVar> container) {
    return container;
  }

  /**
   * Searching a symbol by {@param name} in {@code this} context with {@param accessibility}
   *
   * @param accessibility null if no accessibility check
   */
  @Nullable ContextUnit getUnqualifiedLocalMaybe(
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  );

  default @Nullable ContextUnit getUnqualifiedMaybe(
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    return iterate(c -> c.getUnqualifiedLocalMaybe(name, accessibility, sourcePos));
  }

  default @NotNull ContextUnit getUnqualified(
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    var result = getUnqualifiedMaybe(name, accessibility, sourcePos);
    if (result == null) reportAndThrow(new NameProblem.UnqualifiedNameNotFoundError(this, name, sourcePos));
    return result;
  }

  /**
   * Searching a symbol by qualified id {@code {modName}::{name}} in {@code }this context with {@param accessibility}
   *
   * @param accessibility null if no accessibility check
   */
  @Nullable ContextUnit getQualifiedLocalMaybe(
    @NotNull ModulePath modName,
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  );

  default @Nullable ContextUnit getQualifiedMaybe(
    @NotNull ModulePath modName,
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    return iterate(c -> c.getQualifiedLocalMaybe(modName, name, accessibility, sourcePos));
  }

  default @Nullable ContextUnit getQualifiedMaybe(
    @NotNull QualifiedID qualifiedID,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    return getQualifiedMaybe(qualifiedID.component(), qualifiedID.name(), accessibility, sourcePos);
  }

  /**
   * Searching a symbol by qualified id {@code {modName}::{name}} in the whole context with {@param accessibility}<br/>
   * You should import the module before referring something in it.
   *
   * @param accessibility null if no accessibility check
   */
  default @NotNull ContextUnit getQualified(
    @NotNull ModulePath modName,
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    var result = getQualifiedMaybe(modName, name, accessibility, sourcePos);
    if (result == null)
      reportAndThrow(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
    return result;
  }

  default @NotNull ContextUnit getQualified(
    @NotNull QualifiedID qualifiedID,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    return getQualified(qualifiedID.component(), qualifiedID.name(), accessibility, sourcePos);
  }

  /**
   * Trying to get a {@link MutableModuleExport} of module {@param modName} locally.
   *
   * @param modName qualified module name
   * @return a ModuleExport of that module; null if no such module.
   */
  @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModulePath modName);

  /**
   * Trying to get a {@link MutableModuleExport} of module {@param modName}.
   *
   * @param modName qualified module name
   * @return a ModuleExport of that module; null if no such module.
   */
  default @Nullable ModuleExport getModuleMaybe(@NotNull ModulePath modName) {
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
    var exists = getUnqualifiedMaybe(name, null, sourcePos);
    if (toWarn.test(exists == null ? null : exists.data()) && !name.startsWith(Constants.ANONYMOUS_PREFIX)) {
      reporter().report(new NameProblem.ShadowingWarn(name, sourcePos));
    }
    return new BindContext(this, name, ref);
  }

  default @NotNull ContextUnit.Exportable checkAccessibility(
    @NotNull ContextUnit.Exportable symbol,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    if (symbol.accessibility() == Stmt.Accessibility.Private && accessibility == Stmt.Accessibility.Public) {
      reportAndThrow(new AccessibilityError(sourcePos));
    }

    return symbol;
  }

  default @NotNull PhysicalModuleContext derive(@NotNull String extraName) {
    return new PhysicalModuleContext(this, this.moduleName().appended(extraName));
  }

  default @NotNull PhysicalModuleContext derive(@NotNull Seq<@NotNull String> extraName) {
    return new PhysicalModuleContext(this, this.moduleName().concat(extraName));
  }

  default @NotNull MockModuleContext mock(@NotNull DefVar<?, ?> defVar, @NotNull Stmt.Accessibility accessibility) {
    return new MockModuleContext(this, new ContextUnit.Exportable(defVar, accessibility));
  }

  class ResolvingInterruptedException extends InterruptException {
    @Override public InterruptStage stage() {
      return InterruptStage.Resolving;
    }
  }
}
