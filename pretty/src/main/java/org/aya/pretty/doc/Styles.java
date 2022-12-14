// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.doc;

import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.NotNull;

public record Styles(@NotNull MutableList<Style> styles) {
  public static @NotNull Styles empty() {
    return new Styles(MutableList.create());
  }

  public @NotNull Styles italic() {
    styles.append(Style.italic());
    return this;
  }

  public @NotNull Styles bold() {
    styles.append(Style.bold());
    return this;
  }

  public @NotNull Styles strike() {
    styles.append(Style.strike());
    return this;
  }

  public @NotNull Styles underline() {
    styles.append(Style.underline());
    return this;
  }

  public @NotNull Styles color(@NotNull String colorName) {
    styles.append(Style.color(colorName));
    return this;
  }

  public @NotNull Styles colorBG(@NotNull String colorName) {
    styles.append(Style.colorBg(colorName));
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
    styles.append(Style.colorBg(color));
    return this;
  }

  public @NotNull Styles custom(@NotNull Style.CustomStyle style) {
    styles.append(style);
    return this;
  }

  public @NotNull Styles preset(@NotNull String styleName) {
    styles.append(Style.preset(styleName));
    return this;
  }
}
