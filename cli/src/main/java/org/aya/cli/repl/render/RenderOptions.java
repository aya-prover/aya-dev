// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.render;

import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.printer.Stylist;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  // TODO: used if colorScheme was set to Custom.
  public @Nullable String path = null;

  public @NotNull <T extends Stylist> T buildStylist(BiFunction<ColorScheme, StyleFamily, T> ctor) {
    var colorScheme = this.colorScheme == null
      ? DEFAULT_COLOR_SCHEME
      : this.colorScheme;
    var styleFamily = this.styleFamily == null
      ? DEFAULT_STYLE_FAMILY
      : this.styleFamily;

    return ctor.apply(generateColorScheme(colorScheme), generateStyleFamily(styleFamily));
  }

  public @NotNull ColorScheme buildColorScheme() {
    if (colorScheme == null) return generateColorScheme(DEFAULT_COLOR_SCHEME);
    return generateColorScheme(this.colorScheme);
  }

  public @NotNull StyleFamily buildStyleFamily() {
    if (styleFamily == null) return generateStyleFamily(DEFAULT_STYLE_FAMILY);
    return generateStyleFamily(this.styleFamily);
  }

  public static @NotNull ColorScheme generateColorScheme(@NotNull ColorSchemeName name) {
    return switch (name) {
      case Emacs -> AyaColorScheme.EMACS;
      case IntelliJ -> AyaColorScheme.INTELLIJ;
      case Custom -> {
        throw new UnsupportedOperationException("TODO");
      }
    };
  }

  public static @NotNull StyleFamily generateStyleFamily(@NotNull StyleFamilyName name) {
    return switch (name) {
      case Default -> AyaStyleFamily.DEFAULT;
      case Cli -> AyaStyleFamily.ADAPTIVE_CLI;
    };
  }
}
