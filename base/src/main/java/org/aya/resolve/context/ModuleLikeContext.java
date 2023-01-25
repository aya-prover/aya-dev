// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import org.aya.ref.AnyVar;
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
  @NotNull ModuleSymbol<AnyVar> symbols();

  /**
   * Modules that this module imported.
   */
  @NotNull Map<ModulePath.Qualified, ModuleExport> modules();

  /**
   * Modules that this module exported.
   */
  @NotNull Map<ModulePath, ModuleExport> exports();

  @Override
  default @Nullable ModuleExport getModuleLocalMaybe(@NotNull ModulePath.Qualified modName) {
    return modules().getOrNull(modName);
  }

  @Override default @Nullable AnyVar getUnqualifiedLocalMaybe(
    @NotNull String name,
    @NotNull SourcePos sourcePos
  ) {
    var symbol = symbols().getUnqualifiedMaybe(name);
    if (symbol.isOk()) {
      return symbol.get();
    } else {
      // I am sure that this is not equivalent to null
      return switch (symbol.getErr()) {
        case NotFound -> null;
        case Ambiguous -> reportAndThrow(new NameProblem.AmbiguousNameError(
          name,
          ImmutableSeq.narrow(symbols().resolveUnqualified(name).keysView().map(ModulePath::toImmutableSeq).toImmutableSeq()),
          sourcePos));
      };
    }
  }

  @Override
  default @Nullable AnyVar getQualifiedLocalMaybe(
    @NotNull ModulePath.Qualified modName,
    @NotNull String name,
    @NotNull SourcePos sourcePos
  ) {
    var mod = modules().getOrNull(modName);
    if (mod == null) return null;

    var ref = mod.symbols().getUnqualifiedMaybe(name);
    if (ref.isOk()) return ref.get();

    return switch (ref.getErr()) {
      case NotFound -> reportAndThrow(new NameProblem.QualifiedNameNotFoundError(modName, name, sourcePos));
      case Ambiguous -> reportAndThrow(new NameProblem.AmbiguousNameError(
        name,
        ImmutableSeq.narrow(mod.symbols().resolveUnqualified(name).keysView().map(ModulePath::toImmutableSeq).toImmutableSeq()),
        sourcePos
      ));
    };
  }
}
