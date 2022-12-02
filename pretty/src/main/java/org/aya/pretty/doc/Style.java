// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.doc;

import kala.collection.mutable.MutableList;
import org.aya.pretty.printer.ColorScheme;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Text styles. Inspired by terminal-colors.d(5)
 *
 * @author kiva
 */
public sealed interface Style extends Serializable {
  default Styles and() {
    return new Styles(MutableList.of(this));
  }

  enum Attr implements Style {
    Italic,
    Bold,
    Strike,
    Underline,
  }

  record CodeBlock(@NotNull String language) implements Style {
  }

  /** Inline code, for some backends like Markdown and HTML */
  record InlineCode(@NotNull String language) implements Style {
  }

  record ColorName(@NotNull String colorName, boolean background) implements Style {
  }

  record ColorHex(int color, boolean background) implements Style {
  }

  record Preset(@NotNull String styleName) implements Style {
  }

  /**
   * Make your custom style a subclass of this one. For example:
   * <pre>
   *   enum UnixTermStyle implements CustomStyle {
   *     DoubleUnderline,
   *     CurlyUnderline,
   *   }
   * </pre>
   */
  non-sealed interface CustomStyle extends Style {
  }

  static @NotNull Style italic() {
    return Attr.Italic;
  }

  static @NotNull Style bold() {
    return Attr.Bold;
  }

  static @NotNull Style strike() {
    return Attr.Strike;
  }

  static @NotNull Style code(@NotNull String language) {
    return new InlineCode(language);
  }

  static @NotNull Style code() {
    return code("");
  }

  static @NotNull Style underline() {
    return Attr.Underline;
  }

  static @NotNull Style color(@NotNull String colorName) {
    return new ColorName(colorName, false);
  }

  static @NotNull Style colorBg(@NotNull String colorName) {
    return new ColorName(colorName, true);
  }

  static @NotNull Style color(int color) {
    return new ColorHex(color, false);
  }

  static @NotNull Style color(float r, float g, float b) {
    return new ColorHex(ColorScheme.colorOf(r, g, b), false);
  }

  static @NotNull Style colorBg(int color) {
    return new ColorHex(color, true);
  }

  static @NotNull Style preset(@NotNull String styleName) {
    return new Preset(styleName);
  }
}
