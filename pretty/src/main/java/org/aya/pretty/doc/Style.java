// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.doc;

import org.aya.pretty.printer.ColorScheme;
import org.jetbrains.annotations.NotNull;

/**
 * Text styles. Inspired by terminal-colors.d(5)
 *
 * @author kiva
 */
public sealed interface Style {
  default Styles and() {
    return new Styles(this);
  }

  enum Attr implements Style {
    Italic,
    Bold,
    Strike,
    Underline,
  }

  final record ColorName(@NotNull String colorName, boolean background) implements Style {
  }

  final record ColorHex(int color, boolean background) implements Style {
  }

  final record Preset(@NotNull String styleName) implements Style {
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
