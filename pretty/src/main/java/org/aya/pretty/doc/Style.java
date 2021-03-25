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
    return new StyleBuilder();
  }

  enum Attr implements Style {
    Italic,
    Bold,
    Strike,
    Underline,
  }

  final record Color(@NotNull String colorKey, boolean background) implements Style {
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
    Buffer<Style> styles = Buffer.of();

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

    /**
     * The colorNameOrRGB is one of the following
     * - a color name, like `red`, `cyan`, `pink`, etc.
     * - an exact color value (RGB) starting with `#`, like `#ff00ff`
     * <p>
     * We recommend to use color names instead of RBG values as we allow
     * different color themes to have different values of a same color.
     */
    public @NotNull StyleBuilder color(@NotNull String colorNameOrRGB) {
      styles.append(new Color(colorNameOrRGB, false));
      return this;
    }

    /**
     * @see StyleBuilder#color
     */
    public @NotNull StyleBuilder colorBG(@NotNull String colorNameOrRGB) {
      styles.append(new Color(colorNameOrRGB, true));
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

  /**
   * @see StyleBuilder#color
   */
  static @NotNull Style color(@NotNull String colorNameOrRGB) {
    return new Color(colorNameOrRGB, false);
  }

  /**
   * @see StyleBuilder#color
   */
  static @NotNull Style colorBg(@NotNull String colorNameOrRGB) {
    return new Color(colorNameOrRGB, true);
  }

  static @NotNull Style custom(@NotNull CustomStyle style) {
    return new Custom(style);
  }
}
