// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.Reporter;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Var;
import org.mzi.ref.LocalVar;

/**
 * @author re-xyr
 *
 * Introduces a locally bound variable to the context.
 */
public record BindContext(
  @NotNull Context parent,
  @NotNull String name,
  @NotNull LocalVar ref
) implements Context {
  @Override
  public @NotNull Context parent() {
    return parent;
  }

  @Override
  public @NotNull Reporter reporter() {
    return parent.reporter();
  }

  @Override
  public @Nullable Var getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    if (name.equals(this.name)) return ref;
    else return null;
  }

  @Override
  public @Nullable Var getQualifiedLocalMaybe(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    return null;
  }

  @Override
  public @Nullable MutableMap<String, Var> getModuleLocalMaybe(@NotNull Seq<String> modName, @NotNull SourcePos sourcePos) {
    return null;
  }
}
