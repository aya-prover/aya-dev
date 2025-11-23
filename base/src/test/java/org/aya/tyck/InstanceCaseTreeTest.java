// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Instance;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.states.GlobalInstanceSet;
import org.aya.states.InstanceCaseTree;
import org.aya.states.InstanceSet;
import org.aya.states.TyckState;
import org.aya.states.primitive.PrimFactory;
import org.aya.states.primitive.ShapeFactory;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.reporter.ThrowingReporter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public class InstanceCaseTreeTest {
  /// @param def must have no parameters
  public static @NotNull InstanceCaseTree.Preclause from(@NotNull FnDef def) {
    return new InstanceCaseTree.Preclause(
      SeqView.of(def.result()),
      new Instance.Global(new FnDef.Delegate(def.ref()))
    );
  }

  @Test
  public void test() {
    var result = TyckTest.tyck("""
      open inductive Nat | zro | suc Nat
      open inductive Unit | tt
      open inductive List (A : Type) | nil | cons A (List A)
      inductive Empty
      
      open inductive FakeTypeClass (A : Type) (a : A) (B : Type)
      | inhabited
      
      def candy0 : FakeTypeClass Nat 0 Unit => inhabited
      def candy1 : FakeTypeClass Nat 1 Empty => inhabited
      def candy2 : FakeTypeClass Unit tt Empty => inhabited
      def candy3 : FakeTypeClass (List Nat) nil Nat => inhabited
      def candy4 : FakeTypeClass (List Unit) nil Nat => inhabited
      def candy5 : FakeTypeClass (List Unit) nil Empty => inhabited
      """);

    var names = ImmutableSeq.of(
      "candy0",
      "candy1",
      "candy2",
      "candy3",
      "candy4",
      "candy5"
    );

    var candies = names.map(it -> (FnDef) result.defs().find(def -> def.ref().name().equals(it)).get());
    var preclauses = candies.map(InstanceCaseTreeTest::from);

    var ct = new InstanceCaseTree(new ExprTycker(
      new TyckState(new ShapeFactory(), new PrimFactory()),
      new InstanceSet(new GlobalInstanceSet()),
      new ThrowingReporter(AyaPrettierOptions.debug()),
      ModulePath.of("DUMMY")))
      .buildCaseTree(0, preclauses);

    System.out.println(ct.toDoc(AyaPrettierOptions.debug()).debugRender());
    System.out.println("Optimized:");
    System.out.println(InstanceCaseTree.optimize(ct).getLeftValue().easyToString());
  }
}
