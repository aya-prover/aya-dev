// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.single;

import kala.collection.SeqLike;
import org.aya.cli.CliArgs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record CompilerFlags(
  @NotNull Message message,
  boolean interruptedTrace,
  @Nullable DistillInfo distillInfo,
  @NotNull SeqLike<Path> modulePaths
) {
  public record DistillInfo(
    @NotNull CliArgs.DistillStage distillStage,
    @NotNull CliArgs.DistillFormat distillFormat,
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
