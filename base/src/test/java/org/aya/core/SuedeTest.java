// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core;

import kala.tuple.Unit;
import org.aya.core.def.FnDef;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.TermSerializer;
import org.aya.tyck.TyckDeclTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SuedeTest {
  @Test public void simpleExpr() {
    var defs = TyckDeclTest.successTyckDecls("""
      open data Nat : Set | zero | suc Nat
      def add (a b : Nat) : Nat
       | zero, a => a
       | suc a, b => suc (add a b)
      def test (a : Nat) => add a zero""");
    var addAZero = ((FnDef) defs.last()).body.getLeftValue();
    var ser = addAZero.accept(new TermSerializer(new TermSerializer.SerState()), Unit.unit());
    assertNotNull(ser);
    assertNotNull(ser.de(new SerTerm.DeState()));
  }
}
