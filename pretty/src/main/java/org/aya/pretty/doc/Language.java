// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.doc;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/** Languages supported by {@link Doc#code} and {@link Doc#codeBlock} */
public interface Language {
  @NotNull String displayName();
  @NotNull ImmutableSeq<Language> parentLanguage();

  default boolean isAya() {
    return this == Builtin.Aya || parentLanguage().anyMatch(Language::isAya);
  }

  static @NotNull Language of(@NotNull String displayName) {
    for (var e : Builtin.values()) {
      if (e.displayName.equalsIgnoreCase(displayName)) return e;
    }
    return new Some(displayName);
  }

  record Some(@NotNull String displayName) implements Language {
    @Override public @NotNull ImmutableSeq<Language> parentLanguage() {
      return ImmutableSeq.empty();
    }
  }

  enum Builtin implements Language {
    Plain(""),
    Markdown("markdown"),
    Aya("aya");

    final String displayName;

    Builtin(@NotNull String displayName) {
      this.displayName = displayName;
    }

    @Override public @NotNull String displayName() {
      return displayName;
    }

    @Override public @NotNull ImmutableSeq<Language> parentLanguage() {
      return ImmutableSeq.empty();
    }
  }
}
