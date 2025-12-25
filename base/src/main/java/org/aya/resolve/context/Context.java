// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.context.Candidate;
import org.aya.syntax.context.ContextView;
import org.aya.syntax.ref.*;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/// > There is no "exception" in this library
///
/// Some functions may report error without providing any indicator (such as boolean or Option),
/// if your code needs this information, feel free to change them!
///
/// @author re-xyr
public interface Context extends ContextView {
  @Override
  @Nullable Context parent();

  /// @return all symbols with name {@param name}
  /// @implSpec return null if not found
  @Nullable Candidate<AnyVar> getCandidateLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos);

  /// Trying to get a symbol by unqualified name {@param name} in `this` context.
  default @Nullable Option<AnyVar> getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos, @NotNull Reporter reporter) {
    var candy = getCandidateLocalMaybe(name, sourcePos);
    if (candy == null) return null;
    assert !candy.isEmpty() : "incorrect implementation";
    if (candy.isAmbiguous()) {
      reporter.report(new NameProblem.AmbiguousNameError(name, candy.from(), sourcePos));
      return Option.none();
    }

    return Option.some(candy.get());
  }

  /// @return null if error
  /// @see Context#getUnqualified(String, SourcePos, Reporter)
  default @Nullable AnyVar getUnqualified(@NotNull String name, @NotNull SourcePos sourcePos, @NotNull Reporter reporter) {
    var result = getUnqualifiedMaybe(name, sourcePos, reporter);
    if (result == null) {
      reporter.report(new NameProblem.UnqualifiedNameNotFoundError(this, name, sourcePos));
      return null;
    }
    return result.getOrNull();
  }

  /// @return null if error
  /// @see Context#getQualifiedMaybe(ModuleName.Qualified, String, SourcePos, Reporter)
  default @Nullable AnyVar getQualified(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  ) {
    var result = getQualifiedMaybe(modName, name, sourcePos, reporter);
    if (result == null) {
      reporter.report(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
      return null;
    }
    return result.getOrNull();
  }

  @Override
  @NotNull
  default Context bind(@NotNull LocalVar ref, @NotNull Reporter reporter) {
    return (Context) ContextView.super.bind(ref, reporter);
  }

  @Override
  @NotNull
  default Context bind(@NotNull LocalVar ref, @NotNull Predicate<@Nullable Candidate<AnyVar>> toWarn, @NotNull Reporter reporter) {
    return (Context) ContextView.super.bind(ref, toWarn, reporter);
  }

  default @NotNull Context bind(
    @NotNull String name, @NotNull LocalVar ref,
    @NotNull Predicate<@Nullable Candidate<AnyVar>> toWarn,
    @NotNull Reporter reporter
  ) {
    // do not bind ignored var, and users should not try to use it
    if (ref == LocalVar.IGNORED) return this;
    var exists = getCandidateMaybe(name, ref.definition());
    if (toWarn.test(exists) && (!(ref.generateKind() == GenerateKind.Basic.Anonymous))) {
      reporter.report(new NameProblem.ShadowingWarn(name, ref.definition()));
    }
    return new BindContext(this, name, ref);
  }

  default @NotNull ModuleContext derive(@NotNull String extraName) {
    return derive(new ModulePath(ImmutableSeq.of(extraName)));
  }

  /// Note that this won't increase [QPath#fileLevelSize]
  default @NotNull ModuleContext derive(@NotNull ModulePath extraName) {
    return new PhysicalModuleContext(this, qualifiedPath().derive(extraName));
  }
}
