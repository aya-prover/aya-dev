// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate.parser;

import kala.function.TriFunction;
import org.aya.literate.Literate;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface InterestingLanguage<T extends Literate.CodeBlock> {
  boolean test(@NotNull String language);
  @NotNull T create(@NotNull String language, @NotNull String code, @Nullable SourcePos sourcePos);

  @NotNull InterestingLanguage<Literate.CodeBlock> ALL = of(s -> true);

  static @NotNull InterestingLanguage<Literate.CodeBlock> of(@NotNull String language) {
    return of(language::equalsIgnoreCase);
  }

  static @NotNull InterestingLanguage<Literate.CodeBlock> of(@NotNull Predicate<String> test) {
    return of(test, Literate.CodeBlock::new);
  }

  static <T extends Literate.CodeBlock> @NotNull InterestingLanguage<T> of(
    @NotNull Predicate<String> test,
    @NotNull TriFunction<String, String, SourcePos, T> factory
  ) {
    return new InterestingLanguage<>() {
      @Override public boolean test(@NotNull String language) {
        return test.test(language);
      }

      @Override
      public @NotNull T create(@NotNull String language, @NotNull String code, @Nullable SourcePos sourcePos) {
        return factory.apply(language, code, sourcePos);
      }
    };
  }
}
