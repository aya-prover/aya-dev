// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.aya.prettier.AyaPrettierOptions;
import org.junit.jupiter.api.Test;

import static org.aya.tyck.TyckTest.tyck;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PatternTyckTest {
  @Test public void elim0() {
    var result = tyck("""
      open inductive Nat | O | S Nat
      def lind (a b : Nat) : Nat elim a
      | 0 => b
      | S a' => S (lind a' b)
      """).defs();
    assertTrue(result.isNotEmpty());
    result.forEach(def -> assertTrue(
      def.toDoc(AyaPrettierOptions.pretty()).isNotEmpty()));
  }
}
