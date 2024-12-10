// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

public interface ClassError {
  @Language("Aya") String testNotClassCall = """
    def what (A : Type) : A => new A
    """;

  @Language("Aya") String testNotFullyApplied = """
    class Kontainer
    | walue : ISet
    def what : Kontainer => new Kontainer
    """;

  @Language("Aya") String testUnknownMember = """
    open class Kontainer
    | walue : Set
    def what (k : Kontainer) => k.ummm
    """;

  @Language("Aya") String testSigmaCon = "def bruh : Type => (ISet, Set)";
  @Language("Aya") String testSigmaAcc = "def bruh (A : Type) : Type => A.1";
  @Language("Aya") String testSigmaProj = "def bruh (A : Sig Type ** ISet) : Set => A.3";

  @Language("Aya") String testInstNotFound = """
    open class Ok | A : Type
    def test => A
    """;
}
