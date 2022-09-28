package org.aya.parser.ij;

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
