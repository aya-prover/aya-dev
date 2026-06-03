// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StrictlyPositivityTest {
  @Test
  public void test() {
    var result = TyckTest.tyck("""
    open inductive Nat | zro | suc Nat
    inductive List (A : Type) | nil | cons A (List A)
    inductive Tree (A : Type) | leaf | node A (List (Tree A))
    inductive Arrow (A B : Type) | intro (A -> B)
    inductive Vec Type Nat
    | A, zro => vnil
    | A, suc n => vcons A (Vec A n)
    """);

    assertPositivity(result.defs(), "Nat");
    assertPositivity(result.defs(), "List", true);
    assertPositivity(result.defs(), "Tree", true);
    assertPositivity(result.defs(), "Arrow", false, true);
    assertPositivity(result.defs(), "Vec", true, false);
  }

  public static void assertPositivity(@NotNull ImmutableSeq<TyckDef> defs, @NotNull String name, boolean... covariance) {
    var def = (DataDef) defs.find(x -> x.ref().name().equals(name)).get();
    Assertions.assertArrayEquals(covariance, def.covariance());
  }
}
