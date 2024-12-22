// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.single;

import kala.collection.SeqView;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.utils.CliEnums;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.backend.string.StringPrinterConfig;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record CompilerFlags(
  @NotNull Message message,
  boolean interruptedTrace,
  boolean remake,
  @Nullable CompilerFlags.PrettyInfo prettyInfo,
  @NotNull SeqView<Path> modulePaths,
  @Nullable Path outputFile
) {
  public static @Nullable CompilerFlags.PrettyInfo prettyInfoFromOutput(
    @Nullable Path outputFile, @NotNull RenderOptions renderOptions,
    boolean noCodeStyle, boolean inlineCodeStyle, boolean SSR
  ) {
    if (outputFile != null) return new PrettyInfo(
      false,
      noCodeStyle, inlineCodeStyle, SSR,
      CliEnums.PrettyStage.literate,
      CliEnums.detectFormat(outputFile),
      AyaPrettierOptions.pretty(),
      renderOptions,
      null);
    return null;
  }

  public record PrettyInfo(
    boolean ascii,
    boolean prettyNoCodeStyle,
    boolean prettyInlineCodeStyle,
    boolean prettySSR,
    @NotNull CliEnums.PrettyStage prettyStage,
    @NotNull CliEnums.PrettyFormat prettyFormat,
    @NotNull PrettierOptions prettierOptions,
    @NotNull RenderOptions renderOptions,
    @Nullable String prettyDir
  ) {
    public @NotNull RenderOptions.DefaultSetup backendOpts(boolean headerCode) {
      return new RenderOptions.DefaultSetup(headerCode, !prettyNoCodeStyle, !prettyInlineCodeStyle,
        !ascii, StringPrinterConfig.INFINITE_SIZE, prettySSR);
    }
  }

  public record Message(
    @NotNull String successNotation,
    @NotNull String failNotation
  ) {
    public static final Message EMOJI = new Message("🎉", "🥲");
    public static final Message ASCII = new Message("That looks right!", "Let's learn from that.");
  }
}
