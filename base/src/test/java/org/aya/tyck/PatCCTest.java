// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.tyck.pat.PatClassifier;
import org.junit.jupiter.api.Test;

/**
 * CC = coverage and confluence
 */
public class PatCCTest {
  @Test
  public void addCC() {
    var decls = TyckDeclTest.successTyckDecls("""
      \\open \\data Nat : \\Set | zero | suc Nat
      \\def f (a, b : Nat) : Nat
       | zero, b => b
       | a, zero => a
       | suc a, b => suc (f a b)
       | a, suc b => suc (f a b)""");
    var clauses = ((FnDef) decls.get(1)).body().getRightValue();
    var classified = PatClassifier.classify(clauses);
    System.out.println(classified);
  }
}
