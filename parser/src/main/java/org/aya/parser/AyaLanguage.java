// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.parser;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public class AyaLanguage extends Language {
  public static final @NotNull AyaLanguage INSTANCE = new AyaLanguage();

  protected AyaLanguage() {
    super("Aya");
  }

  @Override public @NotNull String getDisplayName() {
    return "Aya Prover";
  }

  @Override public boolean isCaseSensitive() {
    return true;
  }
}
