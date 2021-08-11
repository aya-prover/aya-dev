// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.context;

import kala.collection.Seq;
import kala.collection.mutable.MutableMap;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Introduces a locally bound variable to the context.
 *
 * @author re-xyr
 */
public record BindContext(
  @NotNull Context parent,
  @NotNull String name,
  @NotNull LocalVar ref
) implements Context {
  @Override public @NotNull Context parent() {
    return parent;
  }

  @Override public @NotNull Reporter reporter() {
    return parent.reporter();
  }

  @Override public @Nullable Var getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
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
