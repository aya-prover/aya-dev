// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

public interface ClassError {
  @Language("Aya") String testNotClassCall = """
    def what (A : Type) : A => new A
    """;

  @Language("Aya") String testNotFullyApplied = """
    inductive Nat | O | S Nat
    class Kontainer
    | walue : Nat

    def what : Kontainer => new Kontainer
    """;
}
