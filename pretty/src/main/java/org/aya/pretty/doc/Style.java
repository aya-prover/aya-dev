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

  class StyleBuilder {
    Buffer<Style> styles = Buffer.of();

    public StyleBuilder italic() {
      styles.append(Attr.Italic);
      return this;
    }

    public StyleBuilder bold() {
      styles.append(Attr.Bold);
      return this;
    }

    public StyleBuilder strike() {
      styles.append(Attr.Strike);
      return this;
    }

    public StyleBuilder underline() {
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
    public StyleBuilder color(@NotNull String colorNameOrRGB) {
      styles.append(new Color(colorNameOrRGB, false));
      return this;
    }

    /**
     * @see StyleBuilder#color
     */
    public StyleBuilder colorBG(@NotNull String colorNameOrRGB) {
      styles.append(new Color(colorNameOrRGB, true));
      return this;
    }
  }

  static Style italic() {
    return Attr.Italic;
  }

  static Style bold() {
    return Attr.Bold;
  }

  static Style strike() {
    return Attr.Strike;
  }

  static Style underline() {
    return Attr.Underline;
  }

  /**
   * @see StyleBuilder#color
   */
  static Style color(@NotNull String colorNameOrRGB) {
    return new Color(colorNameOrRGB, false);
  }

  /**
   * @see StyleBuilder#color
   */
  static Style colorBg(@NotNull String colorNameOrRGB) {
    return new Color(colorNameOrRGB, true);
  }
}
