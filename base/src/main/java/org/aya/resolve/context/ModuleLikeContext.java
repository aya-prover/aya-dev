// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.Stmt;
import org.aya.resolve.error.NameProblem;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * A read only {@link ModuleContext}
 */
public interface ModuleLikeContext extends Context {
  @Override @NotNull Context parent();
  @Override default @NotNull Reporter reporter() {
    return parent().reporter();
  }
  @Override default @NotNull Path underlyingFile() {
    return parent().underlyingFile();
  }

  /**
   * Symbols that available in this module
   */
  @NotNull ModuleSymbol<ContextUnit.TopLevel> symbols();

  /**
   * Modules this module imported (including itself)
   */
  @NotNull Map<ModulePath, ModuleExport> modules();

  /**
   * The things that this module exported.
   */
  @NotNull Map<ModulePath, ModuleExport> exports();

  @Override
  default @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModulePath modName) {
    return modules().getOrNull(modName);
  }

  @Override default @Nullable ContextUnit getUnqualifiedLocalMaybe(
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    var symbol = symbols().getUnqualifiedDefinitely(name);
    if (symbol.isOk()) {
      var result = symbol.get();
      if (result instanceof ContextUnit.Exportable defined) {
        return checkAccessibility(defined, accessibility, sourcePos);
      } else {
        return result;
      }
    } else {
      // I am sure that this is not equivalent to null
      return switch (symbol.getErr()) {
        case NotFound -> null;
        case Ambiguous -> reportAndThrow(new NameProblem.AmbiguousNameError(
          name,
          ImmutableSeq.narrow(symbols().getCandidates(name).keysView().map(ModulePath::toImmutableSeq).toImmutableSeq()),
          sourcePos));
      };
    }
  }

  @Override
  default @Nullable ContextUnit getQualifiedLocalMaybe(
    @NotNull ModulePath modName,
    @NotNull String name,
    @Nullable Stmt.Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    var mod = modules().getOrNull(modName);
    if (mod == null) return null;

    var ref = mod.symbols().getUnqualifiedDefinitely(name);
    if (ref.isOk()) return ref.get();

    return switch (ref.getErr()) {
      case NotFound -> reportAndThrow(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
      case Ambiguous -> reportAndThrow(new NameProblem.AmbiguousNameError(
        name,
        ImmutableSeq.narrow(mod.symbols().getCandidates(name).keysView().map(ModulePath::toImmutableSeq).toImmutableSeq()),
        sourcePos
      ));
    };
  }
}
