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

  sealed abstract class Color implements Style {
    /**
     * The colorKey is one of the following
     * - a color name, like `red`, `cyan`, `pink`, etc.
     * - an exact color value (RGB) starting with `#`, like `#ff00ff`
     * <p>
     * We recommend to use color names instead of RBG values as we allow
     * different color themes to have different values of a same color.
     */
    public @NotNull String colorKey;

    /**
     * Some output device like xterm supports brighter colors.
     */
    public boolean brighter = false;

    private Color(@NotNull String colorKey) {
      this.colorKey = colorKey;
    }

    public Color brighter() {
      this.brighter = true;
      return this;
    }
  }

  final class ColorFG extends Color {
    private ColorFG(@NotNull String colorKey) {
      super(colorKey);
    }
  }

  final class ColorBG extends Color {
    private ColorBG(@NotNull String colorKey) {
      super(colorKey);
    }
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
     * @see Color#colorKey
     */
    public StyleBuilder color(@NotNull String colorNameOrRGB) {
      styles.append(new ColorFG(colorNameOrRGB));
      return this;
    }

    /**
     * @see Color#colorKey
     * @see Color#brighter
     */
    public StyleBuilder colorBrighter(@NotNull String colorNameOrRGB) {
      styles.append(new ColorFG(colorNameOrRGB).brighter());
      return this;
    }

    /**
     * @see Color#colorKey
     */
    public StyleBuilder colorBG(@NotNull String colorNameOrRGB) {
      styles.append(new ColorBG(colorNameOrRGB));
      return this;
    }

    /**
     * @see Color#colorKey
     * @see Color#brighter
     */
    public StyleBuilder colorBgBrighter(@NotNull String colorNameOrRGB) {
      styles.append(new ColorBG(colorNameOrRGB).brighter());
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
   * @see Color#colorKey
   */
  static Style color(@NotNull String colorNameOrRGB) {
    return new ColorFG(colorNameOrRGB);
  }

  /**
   * @see Color#colorKey
   * @see Color#brighter
   */
  static Style colorBrighter(@NotNull String colorNameOrRGB) {
    return new ColorFG(colorNameOrRGB).brighter();
  }

  /**
   * @see Color#colorKey
   */
  static Style colorBg(@NotNull String colorNameOrRGB) {
    return new ColorBG(colorNameOrRGB);
  }

  /**
   * @see Color#colorKey
   * @see Color#brighter
   */
  static Style colorBgBrighter(@NotNull String colorNameOrRGB) {
    return new ColorBG(colorNameOrRGB).brighter();
  }
}
