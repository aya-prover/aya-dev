// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.render;

import com.google.gson.JsonParseException;
import kala.control.Try;
import org.aya.cli.repl.render.vscode.ColorTheme;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.printer.Stylist;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiFunction;

public class RenderOptions {
  public enum ColorSchemeName {
    Emacs,
    IntelliJ,

    /**
     * import color scheme from the {@link RenderOptions#path} which in vscode color theme format.
     */
    Custom
  }

  public enum StyleFamilyName {
    Default,
    Cli
  }

  public final static @NotNull ColorSchemeName DEFAULT_COLOR_SCHEME = ColorSchemeName.Emacs;
  public final static @NotNull StyleFamilyName DEFAULT_STYLE_FAMILY = StyleFamilyName.Cli;

  public @Nullable RenderOptions.ColorSchemeName colorScheme = DEFAULT_COLOR_SCHEME;
  public @Nullable RenderOptions.StyleFamilyName styleFamily = DEFAULT_STYLE_FAMILY;

  public @Nullable String path = null;

  public @NotNull <T extends Stylist> T buildStylist(@NotNull BiFunction<ColorScheme, StyleFamily, T> ctor) throws IOException, JsonParseException {
    return ctor.apply(buildColorScheme(), buildStyleFamily());
  }

  public @NotNull <T extends Stylist> T buildStylistUnchecked(@NotNull BiFunction<ColorScheme, StyleFamily, T> ctor) {
    return Try.of(() -> buildStylist(ctor)).getOrThrow();
  }

  public @NotNull ColorScheme buildColorScheme() throws IOException, JsonParseException {
    if (colorScheme == null) return generateColorScheme(DEFAULT_COLOR_SCHEME, null);
    return generateColorScheme(colorScheme, path == null ? null : Path.of(path));
  }

  public @NotNull StyleFamily buildStyleFamily() {
    if (styleFamily == null) return generateStyleFamily(DEFAULT_STYLE_FAMILY);
    return generateStyleFamily(styleFamily);
  }

  public static @NotNull ColorScheme generateColorScheme(@NotNull ColorSchemeName name, @Nullable Path path) throws IOException, JsonParseException {
    return switch (name) {
      case Emacs -> AyaColorScheme.EMACS;
      case IntelliJ -> AyaColorScheme.INTELLIJ;
      case Custom -> {
        if (path == null) throw new IllegalArgumentException("Unable to generate a custom color scheme without a path");

        // IOException from here
        var colorTheme = ColorTheme.loadFrom(path).<IOException>getOrThrow();

        yield colorTheme.buildColorScheme(null);
      }
    };
  }

  public static @NotNull StyleFamily generateStyleFamily(@NotNull StyleFamilyName name) {
    return switch (name) {
      case Default -> AyaStyleFamily.DEFAULT;
      case Cli -> AyaStyleFamily.ADAPTIVE_CLI;
    };
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RenderOptions that = (RenderOptions) o;
    return colorScheme == that.colorScheme && styleFamily == that.styleFamily && Objects.equals(path, that.path);
  }
}
