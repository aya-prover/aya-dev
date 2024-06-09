// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

@SuppressWarnings("unused")
public interface ParseError {
  String testTrivial = "def";
  @Language("Aya") String testModifier = "overlap inductive E";
  @Language("Aya") String testIgnoredModifier = """
    inline def id {A : Type} A : A
    | a => a
    """;
  @Language("Aya") String testOverlapOnExpr = """
    overlap def id {A : Type} (a : A) => a
    """;
}
