// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import com.google.gson.JsonElement;
import kala.collection.Seq;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.control.Result;
import org.aya.pretty.backend.html.DocHtmlPrinter;
import org.aya.pretty.backend.latex.DocTeXPrinter;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param colorScheme
 * @param styleFamily
 * @param renderTarget Determines the render target,
 *                     the {@param colorScheme} and {@param styleFamily} will be ignored
 *                     if the render target is {@link RenderTarget#Debug}.
 */
public record RenderOptions(
  @NotNull Option<ColorScheme> colorScheme,
  @NotNull Option<StyleFamily> styleFamily,
  @NotNull Option<RenderTarget> renderTarget
) {
  public enum RenderTarget {
    Debug,
    HTML,
    TeX
  }

  public final static RenderOptions DEFAULT = new RenderOptions(
    Option.some(AyaColorScheme.INTELLIJ),
    Option.some(AyaStyleFamily.DEFAULT),
    Option.some(RenderTarget.Debug)
  );

  /// region Helper

  /**
   * Construct a {@link RenderOptions} from {@link ServerOptions}
   */
  @Contract(pure = true)
  public static @NotNull Result<@NotNull RenderOptions, @NotNull String> fromServerOptions(@NotNull ServerOptions options) {
    var rawColorScheme = options.colorScheme;
    var rawStyleFamily = options.styleFamily;
    var renderTarget = options.renderTarget;

    var colorScheme = Option.<ColorScheme>none();
    if (rawColorScheme != null) {
      var result = parseColorScheme(rawColorScheme);
      if (result.isErr()) return Result.err(result.getErr());
      colorScheme = Option.some(result.get());
    }

    var styleFamily = Option.<StyleFamily>none();
    // TODO[hoshino]

    return Result.ok(new RenderOptions(colorScheme, styleFamily, Option.ofNullable(renderTarget)));
  }

  private static Result<ColorScheme, String> parseColorScheme(@NotNull JsonElement colorScheme) {
    if (colorScheme.isJsonPrimitive() && colorScheme.getAsJsonPrimitive().isString()) {
      var colorSchemeName = colorScheme.getAsString();

      if (colorSchemeName.equals("emacs")) {
        return Result.ok(AyaColorScheme.EMACS);
      } else if (colorSchemeName.equals("intellij")) {
        return Result.ok(AyaColorScheme.INTELLIJ);
      } else {
        return Result.err("Invalid color scheme name");
      }
    } else if (colorScheme.isJsonObject()) {
      var colorSchemeObj = colorScheme.getAsJsonObject();
      var colorSchemeMap = MutableMap.<String, Integer>create();

      for (var entry : colorSchemeObj.entrySet()) {
        // TODO[hoshino]: Does gson guarantee the key and the value are NotNull?
        @Nullable var key = entry.getKey();
        @Nullable var value = entry.getValue();
        // TODO[hoshino]: What if we pre-build a keyName set ?
        var isKeyValid = key != null && Seq.from(AyaColorScheme.Key.values())
          .view().map(AyaColorScheme.Key::key)
          .contains(key);

        if (!isKeyValid) return Result.err("Invalid key: " + key);

        var isColorValid = value != null
          && value.isJsonPrimitive()
          && value.getAsJsonPrimitive().isString();

        if (!isColorValid) return Result.err("Invalid color: " + value);

        var colorCode = RenderOptions.parseColor(value.getAsString());

        if (colorCode.isOk()) {
          colorSchemeMap.put(key, colorCode.get());
        } else {
          return Result.err(colorCode.getErr());
        }
      }

      return Result.ok(new AyaColorScheme(colorSchemeMap));
    } else {
      return Result.err("Invalid value");
    }
  }

  /**
   * Parsing the color code into an integer. I didn't find any function do this.
   *
   * @param color the color string, which should be a hexadecimal number string in 6 numbers long, optional prefix "#" and "0x" are allowed.
   */
  public static Result<Integer, String> parseColor(String color) {
    var colorCode = color;
    if (color.charAt(0) == '#') colorCode = colorCode.substring(1);
    if (colorCode.startsWith("0x")) colorCode = colorCode.substring(2);

    if (colorCode.length() != 6) return Result.err("The color code is too long or too short!");

    try {
      var result = Integer.parseInt(colorCode, 16);

      return Result.ok(result);
    } catch (NumberFormatException e) {
      return Result.err(e.getMessage());
    }
  }

  /// endregion

  @Contract(pure = true)
  public @NotNull RenderOptions update(@NotNull RenderOptions other) {
    var colorScheme = other.colorScheme();
    var styleFamily = other.styleFamily();
    var renderTarget = other.renderTarget();

    if (colorScheme.isEmpty()) colorScheme = this.colorScheme();
    if (styleFamily.isEmpty()) styleFamily = this.styleFamily();
    if (renderTarget.isEmpty()) renderTarget = this.renderTarget();

    return new RenderOptions(colorScheme, styleFamily, renderTarget);
  }

  /**
   * Render the {@param doc} with {@link RenderOptions}
   */
  public @NotNull String render(@NotNull Doc doc) {
    var colorScheme = this.colorScheme().getOrDefault(DEFAULT.colorScheme.get());
    var styleFamily = this.styleFamily().getOrDefault(DEFAULT.styleFamily().get());

    return switch (renderTarget().getOrDefault(RenderTarget.Debug)) {
      case Debug -> doc.debugRender();
      case HTML -> doc.render(new DocHtmlPrinter(), new DocHtmlPrinter.Config(colorScheme, styleFamily, false));
      case TeX -> doc.render(new DocTeXPrinter(), new DocTeXPrinter.Config(colorScheme, styleFamily));
    };
  }
}
