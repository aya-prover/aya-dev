// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.SeqLike;
import org.aya.cli.utils.MainArgs;
import org.aya.pretty.printer.ColorScheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record CompilerFlags(
  @NotNull Message message,
  boolean interruptedTrace,
  boolean remake,
  @Nullable DistillInfo distillInfo,
  @NotNull SeqLike<Path> modulePaths,
  @Nullable Path outputFile
) {
  public record DistillInfo(
    @NotNull MainArgs.DistillStage distillStage,
    @NotNull MainArgs.DistillFormat distillFormat,
    @NotNull ColorScheme scheme,
    @NotNull Path distillDir
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
