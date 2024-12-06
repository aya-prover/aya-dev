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
}
