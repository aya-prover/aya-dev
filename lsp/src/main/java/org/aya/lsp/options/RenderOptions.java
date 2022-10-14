// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.options;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.control.Result;
import org.aya.lsp.models.ServerOptions;
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
  @NotNull Option<ColorScheme> colorScheme,
  @NotNull Option<StyleFamily> styleFamily
) {
  public final static RenderOptions DEFAULT = new RenderOptions(
    Option.some(AyaColorScheme.INTELLIJ),
    Option.some(AyaStyleFamily.DEFAULT)
  );

  public enum RenderTarget {
    Debug,
    HTML,
    TeX
  }

  public static final @NotNull ImmutableMap<String, ColorScheme> BUILTIN_COLOR_SCHEMES = ImmutableMap.of(
    "emacs", AyaColorScheme.EMACS,
    "intellij", AyaColorScheme.INTELLIJ,
    "none", AyaColorScheme.EMPTY
  );

  /// region Helper

  /**
   * Construct a {@link RenderOptions} from {@link ServerOptions}
   */
  @Contract(pure = true)
  public static @NotNull Result<@NotNull RenderOptions, @NotNull String> fromServerOptions(@NotNull ServerOptions options) {
    Result<ColorScheme, String> colorScheme = parseColorScheme(options);
    // TODO[hoshino]: styleFaimily

    if (colorScheme.isErr()) return Result.err(colorScheme.getErr());

    return Result.ok(new RenderOptions(colorScheme.getOption(), Option.none()));
  }

  private static Result<ColorScheme, String> parseColorScheme(@NotNull ServerOptions options) {
    var colorName = options.colorName;
    var baseOption = options.colorName == null
      ? Option.some(AyaColorScheme.EMPTY)
      : BUILTIN_COLOR_SCHEMES.getOption(colorName);

    if (baseOption.isEmpty()) {
      return Result.err("Invalid color scheme name");
    }

    var base = baseOption.get();
    // We won't modify the origin ColorScheme
    var newColorScheme = MutableMap.from(base.definedColors());
    var override = options.colorOverride;
    if (override == null || override.isEmpty()) return Result.ok(base);

    boolean modified = false;

    for (var entry : override.entrySet()) {
      var key = entry.getKey();
      var value = entry.getValue();

      var isKeyValid = key != null && Seq.from(AyaColorScheme.Key.values()).view()
        .map(AyaColorScheme.Key::key)
        .contains(key);
      var isValueValid = value != null;   // I don't like to inline this code, sorry!

      if (!isKeyValid) return Result.err("Invalid key: " + key);
      if (!isValueValid) return Result.err("Invalid colorResult: null");

      var colorResult = parseColor(value);
      if (colorResult.isErr()) return Result.err("Invalid colorResult: " + value);

      var color = colorResult.get();
      var oldValue = newColorScheme.put(key, color);

      if (oldValue.isDefined() && ! oldValue.get().equals(color)) {
        modified = true;
      }
    }

    return Result.ok(
      modified ? new AyaColorScheme(newColorScheme) : base);
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

    if (colorScheme.isEmpty()) colorScheme = this.colorScheme();
    if (styleFamily.isEmpty()) styleFamily = this.styleFamily();

    return new RenderOptions(colorScheme, styleFamily);
  }

  /// region Render

  /**
   * Choose the most colorful target which the client supports.
   * Note that the client should support `plaintext` at least.
   *
   * @param capabilities the client capabilities, `plaintext` is returned if the capabilities is empty.
   * @throws IllegalArgumentException is thrown if the capabilities is not empty and invalid.
   */
  public static @NotNull RenderTarget renderTargetFromCapabilities(@NotNull List<String> capabilities)
    throws IllegalArgumentException {
    if (capabilities.isEmpty()) return RenderTarget.Debug;
    if (capabilities.contains(MarkupKind.Markdown)) return RenderTarget.HTML;
    if (capabilities.contains(MarkupKind.PlainText)) return RenderTarget.Debug;

    throw new IllegalArgumentException("Unsupported capabilities: " + capabilities);
  }

  public @NotNull String renderToHtml(@NotNull Doc doc) {
    var colorScheme = this.colorScheme().getOrDefault(DEFAULT.colorScheme().get());
    var styleFamily = this.styleFamily().getOrDefault(DEFAULT.styleFamily().get());

    return doc.render(new DocHtmlPrinter(), new DocHtmlPrinter.Config(colorScheme, styleFamily, false));
  }

  public @NotNull String renderToTeX(@NotNull Doc doc) {
    var colorScheme = this.colorScheme().getOrDefault(DEFAULT.colorScheme().get());
    var styleFamily = this.styleFamily().getOrDefault(DEFAULT.styleFamily().get());

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
