// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.context;

import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.QualifiedID;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QPath;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Predicate;

/// ```
///   Context     --->    ContextView
///      ^                     ^
///      |                     |
/// ModuleContext ---> ModuleContextView
///```
public interface ContextView {
  @Nullable ContextView parent();
  @NotNull Path underlyingFile();

  default <T> @Nullable T iterate(@NotNull Function<@NotNull ContextView, @Nullable T> f) {
    var p = this;
    while (p != null) {
      var result = f.apply(p);
      if (result != null) return result;
      p = p.parent();
    }
    return null;
  }

  default MutableList<LocalVar> collect(@NotNull MutableList<LocalVar> container) {
    return container;
  }

  default @NotNull QPath qualifiedPath() {
    var p = parent();
    assert p != null;
    return p.qualifiedPath();
  }

  /// The path of this module
  default @NotNull ModulePath modulePath() {
    return qualifiedPath().module();
  }

  /// Getting a symbol by name {@param name}.
  ///
  /// @param name an id which probably unqualified
  /// @return null if error
  default @Nullable AnyVar get(@NotNull QualifiedID name, @NotNull Reporter reporter) {
    return switch (name.component()) {
      case ModuleName.ThisRef _ -> getUnqualified(name.name(), name.sourcePos(), reporter);
      case ModuleName.Qualified qualified -> getQualified(qualified, name.name(), name.sourcePos(), reporter);
    };
  }

  /// @see ContextView#get(QualifiedID, Reporter)
  default @Nullable Option<AnyVar> getMaybe(@NotNull QualifiedID name, @NotNull Reporter reporter) {
    return switch (name.component()) {
      case ModuleName.ThisRef _ -> getUnqualifiedMaybe(name.name(), name.sourcePos(), reporter);
      case ModuleName.Qualified qualified -> getQualifiedMaybe(qualified, name.name(), name.sourcePos(), reporter);
    };
  }

  /// @return all symbols with name {@param name}
  /// @implSpec return null if not found
  @Nullable Candidate<AnyVar> getCandidateLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos);

  default @Nullable Candidate<AnyVar> getCandidateMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    return iterate(c -> {
      var candy = c.getCandidateLocalMaybe(name, sourcePos);
      return candy == null || candy.isEmpty() ? null : candy;
    });
  }

  /// Trying to get a symbol by unqualified name {@param name} in `this` context.
  @Nullable Option<AnyVar> getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos, @NotNull Reporter reporter);

  /// Trying to get a symbol which can referred by unqualified name {@param name} in the whole context.
  ///
  /// @param name      the unqualified name
  /// @param sourcePos the source pos for error reporting
  /// @return null if not found, `Option.none()` if error
  /// @see ContextView#getUnqualifiedLocalMaybe
  default @Nullable Option<AnyVar> getUnqualifiedMaybe(@NotNull String name, @NotNull SourcePos sourcePos, @NotNull Reporter reporter) {
    return iterate(c -> c.getUnqualifiedLocalMaybe(name, sourcePos, reporter));
  }

  /// @return null if error
  /// @see ContextView#getUnqualified(String, SourcePos, Reporter)
  @Nullable AnyVar getUnqualified(@NotNull String name, @NotNull SourcePos sourcePos, @NotNull Reporter reporter);

  /// Trying to get a symbol by qualified id `{modName}::{name}` in `this` context
  ///
  /// @return a symbol in component {@param modName}, even it is [#This]; null if not found; Option.none() if error
  @Nullable Option<AnyVar> getQualifiedLocalMaybe(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  );

  /// Trying to get a symbol by qualified id `{modName}::{name}` in the whole context with {@param accessibility}.
  ///
  /// @see ContextView#getQualifiedLocalMaybe(ModuleName.Qualified, String, SourcePos, Reporter)
  default @Nullable Option<AnyVar> getQualifiedMaybe(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  ) {
    return iterate(c -> c.getQualifiedLocalMaybe(modName, name, sourcePos, reporter));
  }

  /// @return null if error
  /// @see ContextView#getQualifiedMaybe(ModuleName.Qualified, String, SourcePos, Reporter)
  @Nullable AnyVar getQualified(
    @NotNull ModuleName.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos,
    @NotNull Reporter reporter
  );

  /// Trying to get a [ModuleExport] by a module {@param modName} in `this` context.
  ///
  /// @param modName qualified module name
  /// @return a ModuleExport of that module; null if no such module.
  @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModuleName.Qualified modName);

  /// Trying to get a [ModuleExport] by a module {@param modName} in the whole context.
  ///
  /// @param modName qualified module name
  /// @return a ModuleExport of that module; null if no such module.
  default @Nullable ModuleExport getModuleMaybe(@NotNull ModuleName.Qualified modName) {
    return iterate(c -> c.getModuleLocalMaybe(modName));
  }

  default @NotNull ContextView bind(@NotNull LocalVar ref, @NotNull Predicate<@Nullable Candidate<AnyVar>> toWarn, @NotNull Reporter reporter) {
    return bind(ref.name(), ref, toWarn, reporter);
  }

  default @NotNull ContextView bind(@NotNull LocalVar ref, @NotNull Reporter reporter) {
    return bind(ref, var -> var instanceof Candidate.Defined<AnyVar> defined
      && defined.get() instanceof LocalVar, reporter);
  }

  @NotNull ContextView bind(
    @NotNull String name, @NotNull LocalVar ref,
    @NotNull Predicate<@Nullable Candidate<AnyVar>> toWarn,
    @NotNull Reporter reporter
  );
}
