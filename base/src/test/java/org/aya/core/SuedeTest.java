// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import kala.tuple.Unit;
import org.aya.core.def.FnDef;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.tyck.TyckDeclTest;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SuedeTest {
  @Test public void simpleExpr() {
    @Language("TEXT") var natPlus = """
      open data Nat : Set | zero | suc Nat
      def add (a b : Nat) : Nat
       | zero, a => a
       | suc a, b => suc (add a b)""";
    suedeLastDef(natPlus);
    suedeLastDef(natPlus + "\n def test (a : Nat) => \\x => add a (add x zero)");
    suedeLastDef("def test (y : Set) => Pi (x : Set -> Set (lsuc 1)) -> Sig (x y) ** x y");
  }

  private void suedeLastDef(@Language("TEXT") @NotNull String code) {
    var defs = TyckDeclTest.successTyckDecls(code);
    var lastTerm = (FnDef) defs.last();
    var ser = lastTerm.accept(new Serializer(new Serializer.State()), Unit.unit());
    assertNotNull(ser);
    assertNotNull(ser.de(new SerTerm.DeState()));
  }
}
