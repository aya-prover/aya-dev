// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate.parser;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public interface InterestingLanguage extends Predicate<String> {
  @NotNull InterestingLanguage ALL = s -> true;
  @NotNull InterestingLanguage NONE = s -> false;
}
