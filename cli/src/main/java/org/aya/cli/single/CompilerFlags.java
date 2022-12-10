// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.SeqLike;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.utils.MainArgs;
import org.aya.util.distill.DistillerOptions;
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
    boolean ascii,
    boolean prettyNoCodeStyle,
    @NotNull MainArgs.DistillStage distillStage,
    @NotNull MainArgs.DistillFormat distillFormat,
    @NotNull DistillerOptions distillerOptions,
    @NotNull RenderOptions renderOptions,
    @Nullable String distillDir
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
