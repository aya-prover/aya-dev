// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.SeqLike;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.utils.MainArgs;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record CompilerFlags(
  @NotNull Message message,
  boolean interruptedTrace,
  boolean remake,
  @Nullable CompilerFlags.PrettyInfo prettyInfo,
  @NotNull SeqLike<Path> modulePaths,
  @Nullable Path outputFile
) {
  public record PrettyInfo(
    boolean ascii,
    boolean prettyNoCodeStyle,
    @NotNull MainArgs.PrettyStage prettyStage,
    @NotNull MainArgs.PrettyFormat prettyFormat,
    @NotNull PrettierOptions prettierOptions,
    @NotNull RenderOptions renderOptions,
    @Nullable String prettyDir
  ) {
  }

  public record Message(
    @NotNull String successNotion,
    @NotNull String failNotion
  ) {
    public static final Message EMOJI = new Message("\uD83D\uDC02\uD83C\uDF7A", "\uD83D\uDD28");
    public static final Message ASCII = new Message("That looks right!", "What are you doing?");
  }
}
