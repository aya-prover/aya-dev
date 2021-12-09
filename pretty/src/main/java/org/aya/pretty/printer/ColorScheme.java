// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.printer;

import kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;

public interface ColorScheme {
  @NotNull MutableMap<String, Integer> definedColors();

  static int colorOf(float r, float g, float b) {
    var red = (int) (r * 0xFF);
    var green = (int) (g * 0xFF);
    var blue = (int) (b * 0xFF);
    return red << 16 | green << 8 | blue;
  }
}
