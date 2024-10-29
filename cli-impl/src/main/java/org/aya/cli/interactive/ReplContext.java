// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.interactive;

import org.aya.resolve.context.*;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.RepoLike;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReplContext extends PhysicalModuleContext implements RepoLike<ReplContext> {
  private @Nullable ReplContext downstream = null;

  public ReplContext(@NotNull Context parent, @NotNull ModulePath name) {
    super(parent, name);
  }

  @Override public void importSymbol(
    @NotNull AnyVar ref,
    @NotNull ModuleName fromModule,
    @NotNull String name,
    @NotNull Stmt.Accessibility acc,
    @NotNull SourcePos sourcePos
  ) {
    // REPL always overwrites symbols.
    symbols().add(name, ref, fromModule);
    if (ref instanceof DefVar<?, ?> defVar && acc == Stmt.Accessibility.Public) exportSymbol(name, defVar);
  }

  @Override public boolean exportSymbol(@NotNull String name, @NotNull AnyDefVar ref) {
    super.exportSymbol(name, ref);
    // REPL always overwrites symbols.
    return true;
  }

  @Override public void importModule(
    @NotNull ModuleName.Qualified modName,
    @NotNull ModuleExport mod,
    Stmt.@NotNull Accessibility accessibility,
    @NotNull SourcePos sourcePos
  ) {
    modules.put(modName, mod);
    if (accessibility == Stmt.Accessibility.Public) exports.export(modName, mod);
  }

  @Override public @NotNull ReplContext derive(@NotNull ModulePath extraName) {
    return new ReplContext(this, this.modulePath().derive(extraName));
  }

  @Override public @NotNull ReplContext derive(@NotNull String extraName) {
    return new ReplContext(this, this.modulePath().derive(extraName));
  }

  @Override public void setDownstream(@Nullable ReplContext downstream) {
    this.downstream = downstream;
  }

  public @NotNull ReplContext fork() {
    var kid = derive(":theKid");
    fork(kid);
    return kid;
  }

  @Override public void merge() {
    var bors = downstream;
    RepoLike.super.merge();
    if (bors == null) return;
    mergeSymbols(symbols, bors.symbols);
    exports.symbols().putAll(bors.exports.symbols());
    exports.modules().putAll(bors.exports.modules());
    modules.putAll(bors.modules);
  }

  @Contract(mutates = "this") public void clear() {
    modules.clear();
    exports.symbols().clear();
    exports.modules().clear();
    symbols.table().clear();
  }

  /**
   * @apiNote It is possible that putting {@link ModuleName.Qualified} and {@link ModuleName.ThisRef} to the same name,
   * so be careful about {@param rhs}
   */
  private static <T> void mergeSymbols(@NotNull ModuleSymbol<T> dest, @NotNull ModuleSymbol<T> src) {
    for (var key : src.table().keysView()) {
      var candy = dest.get(key);
      dest.table().put(key, candy.merge(src.get(key)));
    }
  }
}
