// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty.doc;

import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

/**
 * Text styles. Inspired by terminal-colors.d(5)
 *
 * @author kiva
 */
public sealed interface Style {
  default StyleBuilder and() {
    return new StyleBuilder(this);
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

  /**
   * Make your custom style a subclass of this one. For example:
   * <pre>
   *   enum UnixTermStyle implements CustomStyle {
   *     DoubleUnderline,
   *     CurlyUnderline,
   *   }
   * </pre>
   * and use it with {@link Style.Custom} like this
   * <pre>
   *   .custom(UnixTermStyle.CurlyUnderline)
   * </pre>
   */
  interface CustomStyle {
  }

  final record Custom(@NotNull CustomStyle style) implements Style {
  }

  class StyleBuilder {
    Buffer<Style> styles;

    StyleBuilder(Style style) {
      this.styles = Buffer.of(style);
    }

    public @NotNull StyleBuilder italic() {
      styles.append(Attr.Italic);
      return this;
    }

    public @NotNull StyleBuilder bold() {
      styles.append(Attr.Bold);
      return this;
    }

    public @NotNull StyleBuilder strike() {
      styles.append(Attr.Strike);
      return this;
    }

    public @NotNull StyleBuilder underline() {
      styles.append(Attr.Underline);
      return this;
    }

    public @NotNull StyleBuilder color(@NotNull String colorName) {
      styles.append(new ColorName(colorName, false));
      return this;
    }

    public @NotNull StyleBuilder colorBG(@NotNull String colorName) {
      styles.append(new ColorName(colorName, true));
      return this;
    }

    public @NotNull StyleBuilder color(int color) {
      styles.append(new ColorHex(color, false));
      return this;
    }

    public @NotNull StyleBuilder colorBG(int color) {
      styles.append(new ColorHex(color, true));
      return this;
    }

    public @NotNull StyleBuilder custom(@NotNull CustomStyle style) {
      styles.append(new Custom(style));
      return this;
    }
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

  static @NotNull Style colorBg(int color) {
    return new ColorHex(color, true);
  }

  static @NotNull Style custom(@NotNull CustomStyle style) {
    return new Custom(style);
  }
}
