// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.doc;

import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.NotNull;

public record Styles(MutableList<Style> styles) {
  public @NotNull Styles italic() {
    styles.append(Style.Attr.Italic);
    return this;
  }

  public @NotNull Styles bold() {
    styles.append(Style.Attr.Bold);
    return this;
  }

  public @NotNull Styles strike() {
    styles.append(Style.Attr.Strike);
    return this;
  }

  public @NotNull Styles code() {
    styles.append(Style.Attr.Code);
    return this;
  }

  public @NotNull Styles underline() {
    styles.append(Style.Attr.Underline);
    return this;
  }

  public @NotNull Styles color(@NotNull String colorName) {
    styles.append(new Style.ColorName(colorName, false));
    return this;
  }

  public @NotNull Styles colorBG(@NotNull String colorName) {
    styles.append(new Style.ColorName(colorName, true));
    return this;
  }

  public @NotNull Styles color(int color) {
    styles.append(Style.color(color));
    return this;
  }

  public @NotNull Styles color(float r, float g, float b) {
    styles.append(Style.color(r, g, b));
    return this;
  }

  public @NotNull Styles colorBG(int color) {
    styles.append(new Style.ColorHex(color, true));
    return this;
  }

  public @NotNull Styles custom(@NotNull Style.CustomStyle style) {
    styles.append(style);
    return this;
  }

  public @NotNull Styles preset(@NotNull String styleName) {
    styles.append(new Style.Preset(styleName));
    return this;
  }
}
