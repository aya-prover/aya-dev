// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;


import org.aya.concrete.stmt.Decl;
import org.aya.test.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test if the tycker is functioning, say, working for simple cases.
 *
 * @author ice1000
 */
public class TyckExprTest {
  public static @NotNull ExprTycker tycker() {
    return new ExprTycker(ThrowingReporter.INSTANCE, null);
  }

  @Test public void levelEqns() {
    var decls = TyckDeclTest.successDesugarDecls("""
      ulevel uu
      hlevel hh
      def Empty : Type (lsuc hh) (lsuc uu) => Pi (A : Type hh uu) -> A
      def neg (A : Type hh uu) : Type (lsuc (lsuc hh)) (lsuc (lsuc uu)) => A -> Empty
      def P (A : Type hh uu) : Type (lsuc hh) (lsuc uu) => A -> Type hh uu
      def U => Pi (X : Type) (f : P (P X) -> X) -> P (P X)""");

    decls.dropLast(1).forEach(decl -> {
      if (decl instanceof Decl signatured) signatured.tyck(ThrowingReporter.INSTANCE, null);
    });
    var decl = (Decl.FnDecl) decls.last();
    var expr = decl.body.getLeftValue();
    var tycker = tycker();
    tycker.checkNoZonk(expr, null);
    assertFalse(tycker.levelEqns.forZZS().isEmpty());
  }
}
