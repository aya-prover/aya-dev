// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.normalize.Normalizer;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.CorePrettier;
import org.aya.states.TyckState;
import org.aya.syntax.SyntaxTestUtil;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.PrimDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MapLocalCtx;
import org.aya.tyck.tycker.Stateful;
import org.aya.unify.Unifier;
import org.aya.util.Decision;
import org.aya.util.Ordering;
import org.aya.util.position.SourcePos;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
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
      
      // case 3: distributivity of ∧f over ∨f
      def test3L (φ ψ χ : Cof) => φ ∧f (ψ ∨f χ)
      def test3R (φ ψ χ : Cof) => (φ ∧f ψ) ∨f (φ ∧f χ)
      
      // case 4: distributivity of ∧f over ∨f (other direction)
      def test4L (φ ψ χ : Cof) => (φ ∧f ψ) ∨f (φ ∧f χ)
      def test4R (φ ψ χ : Cof) => φ ∧f (ψ ∨f χ)
      """);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "2"})
  public void testCofConversion(String caseNum) {
    var testL = (FnDef) definfo.defs().find(d -> d.ref().name().equals("test" + caseNum + "L")).get();
    var testR = (FnDef) definfo.defs().find(d -> d.ref().name().equals("test" + caseNum + "R")).get();
    var paramsSize = Math.max(testL.telescope().size(), testR.telescope().size());
    var params = ImmutableSeq.fill(paramsSize, i -> new LocalVar(Character.toString('a' + i))).view();
    var normalizer = new Normalizer(definfo.info().makeTyckState());

    var leftTerm = testL.body().getLeftValue().instTeleVar(params.take(testL.telescope().size()));
    var rightTerm = testR.body().getLeftValue().instTeleVar(params.take(testR.telescope().size()));

    var left = normalizer.expand(leftTerm);
    var right = normalizer.expand(rightTerm);
    assertNotNull(left);
    assertNotNull(right);

    Stateful tool = () -> normalizer.state;
    if (!tool.withConnection(left, () -> tool.state().isTrue(right))) {
      var prettier = new CorePrettier(AyaPrettierOptions.debug());
      fail("In test" + caseNum + "L and R, the proposition (" +
        prettier.visitCofDisj(left).debugRender() + ") does not imply (" +
        prettier.visitCofDisj(right).debugRender() + ")");
    }
  }
}
