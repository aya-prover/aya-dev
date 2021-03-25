// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import org.aya.core.def.Def;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckDeclTest;
import org.aya.tyck.TyckExprTest;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class DistillerTest {
  @Test public void term() {
    assertFalse(TyckExprTest.lamConnected().toDoc().renderToHtml().isEmpty());
  }

  @Test public void fn() {
    var doc1 = declDoc("\\def id {A : \\Set} (a : A) : A => a");
    var doc2 = declDoc("\\def id {A : \\Set} (a : A) => a");
    var doc3 = declDoc("""
      \\def curry3 (A, B, C, D : \\Set)
                  (f : \\Pi (x : \\Sig A B ** C) -> D)
                  (a : A) (b : B) (c : C) : D
        => f (a, b, c)
      \\def uncurry3 (A : \\Set) (B : \\Set) (C : \\Set) (D : \\Set)
                    (f : \\Pi A B C -> D)
                    (p : \\Sig A B ** C) : D
        => f (p.1) (p.2) (p.3)""");
    assertFalse(Doc.cat(doc1, doc2, doc3).renderToHtml().isEmpty());
  }

  @Test public void data() {
    assertFalse(declDoc("""
      \\open \\data Nat : \\Set | zero | suc Nat
      \\open \\data Int : \\Set | pos Nat | neg Nat { | zero => pos zero }
      \\open \\data Fin (n : Nat) : \\Set | suc m => fzero | suc m => fsuc (Fin m)
      """).renderToHtml().isEmpty());
  }

  @NotNull private Doc declDoc(@Language("TEXT") String text) {
    return Doc.vcat(TyckDeclTest.successTyckDecls(text).map(Def::toDoc));
  }
}
