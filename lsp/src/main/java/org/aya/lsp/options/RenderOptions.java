// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.options;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableMap;
import kala.control.Result;
import org.aya.lsp.models.ServerOptions;
import org.aya.lsp.utils.Log;
import org.aya.pretty.backend.html.DocHtmlPrinter;
import org.aya.pretty.backend.latex.DocTeXPrinter;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.javacs.lsp.MarkupKind;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * @param colorScheme
 * @param styleFamily
 */
public record RenderOptions(
  @NotNull ColorScheme colorScheme,
  @NotNull StyleFamily styleFamily
) {
  public enum RenderTarget {
    Debug,
    HTML,
    TeX
  }

  public static final @NotNull RenderOptions DEFAULT = new RenderOptions(
    AyaColorScheme.EMPTY,
    AyaStyleFamily.DEFAULT
  );

  public static final @NotNull ImmutableMap<String, ColorScheme> BUILTIN_COLOR_SCHEMES = ImmutableMap.of(
    "emacs", AyaColorScheme.EMACS,
    "intellij", AyaColorScheme.INTELLIJ
  );

  /// region Helper

  /**
   * Construct a {@link RenderOptions} from {@link ServerOptions}.
   */
  @Contract(value = "_ -> new", pure = true)
  public static @NotNull RenderOptions fromServerOptions(@NotNull ServerOptions options) {
    ColorScheme colorScheme = parseColorScheme(options);
    // TODO[hoshino]: styleFamily

    return new RenderOptions(colorScheme, AyaStyleFamily.DEFAULT);
  }

  /**
   * Construct a {@link ColorScheme} from {@link ServerOptions}.
   * @apiNote the key that doesn't exist in {@link AyaColorScheme.Key} is ignored.
   *          the color that is invalid is ignored.
   */
  private static @NotNull ColorScheme parseColorScheme(@NotNull ServerOptions options) {
    var colorName = options.colorName;
    var colorOverride = options.colorOverride;
    var baseColorScheme = colorName == null
      ? AyaColorScheme.EMPTY
      : BUILTIN_COLOR_SCHEMES.getOrDefault(colorName, AyaColorScheme.EMPTY);

    if (colorOverride == null || colorOverride.isEmpty()) return baseColorScheme;

    var newMap = MutableMap.from(baseColorScheme.definedColors());

    for (var entry : colorOverride.entrySet()) {
      var key = entry.getKey();
      var value = entry.getValue();

      var isKeyValid = key != null && Seq.from(AyaColorScheme.Key.values()).view()
        .map(AyaColorScheme.Key::key)
        .contains(key);
      var isValueValid = value != null;

      if (! isKeyValid) {
        doWarn("Invalid key: " + key);
        continue;
      }

      if (! isValueValid) {
        doWarn("Invalid value: null");
        continue;
      }

      var color = parseColor(value);
      if (color.isErr()) {
        doWarn("Invalid color: " + value);
        continue;
      }

      newMap.put(key, color.get());
    }

    return new AyaColorScheme(newMap);
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

  public static void doWarn(@NotNull String message) {
    Log.w("%s", message);
  }

  /// endregion

  /// region Render

  /**
   * Choose the most colorful target which the client supports.
   * Note that the client should support `plaintext` at least.
   *
   * @param capabilities the client capabilities, `plaintext` is returned if the capabilities is empty.
   * @throws IllegalArgumentException is thrown if the capabilities is not empty and invalid.
   */
  public static @NotNull RenderTarget renderTargetFromCapabilities(@NotNull List<String> capabilities) {
    if (capabilities.isEmpty()) return RenderTarget.Debug;
    if (capabilities.contains(MarkupKind.Markdown)) return RenderTarget.HTML;
    if (capabilities.contains(MarkupKind.PlainText)) return RenderTarget.Debug;

    doWarn("Invalid capabilities: " + capabilities + ". Ignored.");

    // The client should support `plaintext` at least.
    return RenderTarget.Debug;
  }

  public @NotNull String renderToHtml(@NotNull Doc doc) {
    var colorScheme = this.colorScheme();
    var styleFamily = this.styleFamily();

    return doc.render(new DocHtmlPrinter(), new DocHtmlPrinter.Config(colorScheme, styleFamily, false));
  }

  public @NotNull String renderToTeX(@NotNull Doc doc) {
    var colorScheme = this.colorScheme();
    var styleFamily = this.styleFamily();

    return doc.render(new DocTeXPrinter(), new DocTeXPrinter.Config(colorScheme, styleFamily));
  }

  public @NotNull String renderToString(@NotNull Doc doc) {
    return doc.debugRender();
  }

  /**
   * @see RenderOptions#renderTargetFromCapabilities(List)
   */
  public @NotNull String renderWithCapabilities(@NotNull Doc doc, @NotNull List<String> kinds) throws IllegalArgumentException {
    var target = RenderOptions.renderTargetFromCapabilities(kinds);

    return switch (target) {
      case Debug -> renderToString(doc);
      case HTML -> renderToHtml(doc);
      case TeX -> renderToTeX(doc);         // unreachable for now.
    };
  }

  /// endregion


  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RenderOptions that)) return false;
    return colorScheme.equals(that.colorScheme) && styleFamily.equals(that.styleFamily);
  }

  @Override
  public int hashCode() {
    return Objects.hash(colorScheme, styleFamily);
  }
}
