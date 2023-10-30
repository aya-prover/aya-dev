// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.parser;

import com.intellij.lang.Language;

public class FlclLanguage extends Language {
  public static final FlclLanguage INSTANCE = new FlclLanguage();

  private FlclLanguage() {
    super("Fake Language");
  }
}
