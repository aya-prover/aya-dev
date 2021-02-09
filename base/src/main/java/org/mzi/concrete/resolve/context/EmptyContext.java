// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.resolve.context;

import org.glavo.kala.collection.Seq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.Reporter;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Var;

/**
 * @author re-xyr
 *
 * @apiNote in each file's dependency tree there should be one and only one EmptyContext which is also the tree root.
 * @implNote EmptyContext is the context storing the file's Reporter in the resolving stage.
 */
public record EmptyContext(
  @NotNull Reporter reporter
) implements Context {
  @Override
  public @Nullable Context getParent() {
    return null;
  }

  @Override
  public @NotNull Reporter getReporter() {
    return reporter;
  }

  @Override
  public @Nullable Var getUnqualifiedLocalMaybe(@NotNull String name, @NotNull SourcePos sourcePos) {
    return null;
  }

  @Override
  public @Nullable Var getQualifiedLocalMaybe(@NotNull Seq<@NotNull String> modName, @NotNull String name, @NotNull SourcePos sourcePos) {
    return null;
  }
}
