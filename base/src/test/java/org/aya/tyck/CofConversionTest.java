// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.normalize.Normalizer;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.prettier.CorePrettier;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.term.xtt.CofNF;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.tycker.Stateful;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class CofConversionTest {
  static TyckTest.TyckResult definfo;

  @BeforeAll
  public static void init() {
    definfo = TyckTest.tyck("""
      prim I : ISet
      prim Cof : Set
      module cof-operators {
        prim cofAnd (φ ψ : Cof) : Cof
        prim cofOr (φ ψ : Cof) : Cof
        prim cofEq (i j : I) : Cof
      }
      public open cof-operators using
        ( cofAnd as infixl ∧f
        , cofOr as infixl ∨f
        , cofEq as infix =f)
      
      // case 1: symmetry of equality
      def test1L (i j : I) => i =f j
      def test1R (i j : I) => j =f i
      
      // case 2: transitivity of equality
      def test2L (i j k : I) => (i =f k) ∧f (k =f j)
      def test2R (i j : I) => i =f j
      
      // case 3: distributivity of ∧f over ∨f, using an equality just to make it more complicated
      def test3L (φ ψ : Cof) (i j : I) => φ ∧f (ψ ∨f (i =f j))
      def test3R (φ ψ : Cof) (i j : I) => (φ ∧f ψ) ∨f (φ ∧f (i =f j))
      
      // case 4: distributivity of ∧f over ∨f (other direction)
      def test4L (φ ψ : Cof) (i j : I) => (φ ∧f ψ) ∨f (φ ∧f (i =f j))
      def test4R (φ ψ : Cof) (i j : I) => φ ∧f (ψ ∨f (i =f j))
      
      // case 5: transitivity of ∧f
      def test5L (φ ψ χ : Cof) => (φ ∧f ψ) ∧f χ
      def test5R (φ ψ χ : Cof) => φ ∧f (ψ ∧f χ)
      
      // case 6: transitivity of ∨f
      def test6L (φ ψ χ : Cof) => (φ ∨f ψ) ∨f χ
      def test6R (φ ψ χ : Cof) => φ ∨f (ψ ∨f χ)
      
      // case 7: exfalso
      def test7L (i : I) => (i =f 0) ∧f (i =f 1)
      def test7R (i : I) (φ : Cof) => φ
      
      // case 8: weakening
      def test8L (φ : Cof) => φ
      def test8R (φ : Cof) => φ ∧f φ
      
      // cocase 1: not all propositions are true
      def cotest1L (i : I) => (i =f 1)
      def cotest1R (i : I) (φ : Cof) => φ
      
      // cocase 2: the reversed version of transitivity is not true, because there is no knowledge about k
      def cotest2L (i j : I) => i =f j
      def cotest2R (i j k : I) => (i =f k) ∧f (k =f j)
      """);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "2", "3", "4", "5", "6", "7", "8"})
  public void testCofImplies(String caseNum) {
    var result = computeImplication(caseNum, "test");
    if (!result.implies()) {
      var prettier = new CorePrettier(AyaPrettierOptions.debug());
      fail("In test" + caseNum + "L and R, the proposition (" +
        prettier.visitCofDisj(result.left()).debugRender() + ") does not imply (" +
        prettier.visitCofDisj(result.right()).debugRender() + ")");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "2"})
  public void testCofNotImplies(String caseNum) {
    var result = computeImplication(caseNum, "cotest");
    if (result.implies()) {
      var prettier = new CorePrettier(AyaPrettierOptions.debug());
      fail("In cotest" + caseNum + "L and R, the proposition (" +
        prettier.visitCofDisj(result.left()).debugRender() + ") incorrectly implied (" +
        prettier.visitCofDisj(result.right()).debugRender() + ")");
    }
  }

  private static @NotNull CofConversionTest.ImplicationResult computeImplication(String caseNum, String prefix) {
    var testL = (FnDef) definfo.defs().find(d -> d.ref().name().equals(prefix + caseNum + "L")).get();
    var testR = (FnDef) definfo.defs().find(d -> d.ref().name().equals(prefix + caseNum + "R")).get();
    var paramsSize = Math.max(testL.telescope().size(), testR.telescope().size());
    var params = ImmutableSeq.fill(paramsSize, i -> new LocalVar(Character.toString('a' + i))).view();

    var leftTerm = testL.body().getLeftValue().instTeleVar(params.take(testL.telescope().size()));
    var rightTerm = testR.body().getLeftValue().instTeleVar(params.take(testR.telescope().size()));

    var normalizer = new Normalizer(definfo.info().makeTyckState());
    var left = normalizer.expand(leftTerm);
    var right = normalizer.expand(rightTerm);
    assertNotNull(left);
    assertNotNull(right);

    Stateful tool = () -> normalizer.state;
    return new ImplicationResult(left, right, tool.withConnection(left, () -> tool.state().isTrue(right)));
  }

  private record ImplicationResult(
    CofNF.OrVar<CofNF.Disj> left,
    CofNF.OrVar<CofNF.Disj> right, boolean implies
  ) { }
}
