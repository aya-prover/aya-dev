// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.doc;

import kala.collection.mutable.MutableList;
import org.aya.pretty.printer.ColorScheme;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  }

  sealed interface Color extends Style {
  }

  record LineThrough(@NotNull Position position, @NotNull Shape shape, @Nullable Color color) implements Style {
    public enum Position {Underline, Overline, Strike}
    public enum Shape {Solid, Curly}
  }

  record ColorName(@NotNull String colorName, boolean background) implements Color {
  }

  record ColorHex(int color, boolean background) implements Color {
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
    return new LineThrough(LineThrough.Position.Strike, LineThrough.Shape.Solid, null);
  }

  static @NotNull Style underline() {
    return new LineThrough(LineThrough.Position.Underline, LineThrough.Shape.Solid, null);
  }

  static @NotNull Color color(@NotNull String colorName) {
    return new ColorName(colorName, false);
  }

  static @NotNull Color colorBg(@NotNull String colorName) {
    return new ColorName(colorName, true);
  }

  static @NotNull Color color(int color) {
    return new ColorHex(color, false);
  }

  static @NotNull Color color(float r, float g, float b) {
    return new ColorHex(ColorScheme.colorOf(r, g, b), false);
  }

  static @NotNull Color colorBg(int color) {
    return new ColorHex(color, true);
  }

  static @NotNull Style preset(@NotNull String styleName) {
    return new Preset(styleName);
  }
}
