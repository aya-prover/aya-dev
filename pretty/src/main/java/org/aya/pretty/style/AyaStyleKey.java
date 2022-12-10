// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.style;

import org.aya.pretty.doc.Style;

public enum AyaStyleKey {
  Keyword("aya:Keyword"),
  Fn("aya:Fn"),
  Prim("aya:Primitive"),
  Data("aya:Data"),
  Con("aya:Constructor"),
  Struct("aya:Struct"),
  Field("aya:Field"),
  Generalized("aya:Generalized");

  private final String key;

  AyaStyleKey(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public Style preset() {
    return Style.preset(key());
  }
}
