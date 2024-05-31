// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleCallback;
import org.aya.syntax.SyntaxTestUtil;
import org.aya.syntax.core.def.TyckDef;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TyckTest {
  @Test public void test0() {
    var result = tyck("""
      inductive Nat | O | S Nat
      inductive FreeMonoid (A : Type) | e | cons A (FreeMonoid A)
      
      def id {A : Type} (a : A) => a
      def lam (A : Type) : Fn (a : A) -> Type => fn a => A
      def tup (A : Type) (B : A -> Type) (a : A) (b : Fn (a : A) -> B a)
        : Sig (a : A) ** B a => (id a, id (b a))
      def letExample (A : Type) (B : A -> Type) (f : Fn (a : A) -> B a) (a : A) : B a =>
        let b : B a := f a in b
      """).defs;
    assertTrue(result.isNotEmpty());
  }

  @Test public void test1() {
    var result = tyck("""
      open inductive Unit | unit
      variable A : Type
      def foo {value : A} : A => value
      def what : Unit => foo {value := unit}
      """).defs;
    assertTrue(result.isNotEmpty());
  }

  @Test public void path0() {
    var result = tyck("""
      inductive Nat
      | O : Nat
      | S (x : Nat) : Nat
      prim I : ISet
      prim Path (A : I -> Type) (a : A 0) (b : A 1) : Type
      prim coe (r s : I) (A : I -> Type) : A r -> A s
      
      def transp (A : I -> Type) (a : A 0) : A 1 => coe 0 1 A a
      def transpInv (A : I -> Type) (a : A 1) : A 0 => coe 1 0 A a
      def coeFill0 (A : I -> Type) (u : A 0) : Path A u (transp A u) => \\i => coe 0 i A u
      """).defs;
    assertTrue(result.isNotEmpty());
  }

  @Test public void path1() {
    var result = tyck("""
      inductive Nat | O | S Nat
      prim I : ISet
      prim Path (A : I -> Type) (a : A 0) (b : A 1) : Type
      prim coe
      variable A : Type
      def infix = (a b : A) => Path (\\i => A) a b
      def refl {a : A} : a = a => \\i => a
      open inductive Int
      | pos Nat | neg Nat
      | zro : pos 0 = neg 0
      example def testZro0 : zro 0 = pos 0 => refl
      example def testZro1 : zro 1 = neg 0 => refl
      def funExt (A B : Type) (f g : A -> B) (p : Fn (a : A) -> f a = g a) : f = g =>
        \\ i => \\ a => p a i
      def pinv {a b : A} (p : a = b) : b = a => coe 0 1 (\\i => p i = a) refl
      """).defs;
    assertTrue(result.isNotEmpty());
  }

  /// Need pruning
  /*@Test*/
  public void issue768() {
    var result = tyck("""
      open inductive Unit | unit
      inductive Nat | O | S Nat
      open inductive SomeDT Nat
      | m => someDT
      def how' {m : Nat} (a : Nat) (b : SomeDT m) : Nat => 0
      def what {A : Nat -> Type} (B : Fn (n : Nat) -> A n -> Nat) : Unit => unit
      def boom => what (fn n => fn m => how' 0 m)
      """).defs;
    assertTrue(result.isNotEmpty());
  }

  @Test public void test2() {
    var result = tyck("""
      open inductive Nat | O | S Nat
      open inductive Bool | true | false
      def not Bool : Bool | true => false | false => true
      def even Nat : Bool
      | 0 => true
      | S n => odd n
      def odd Nat : Bool
      | 0 => false
      | S n => even n
      """).defs;
    assertTrue(result.isNotEmpty());
  }

  @Test public void test3() {
    assertTrue(tyck("""
      open inductive Nat | O | S Nat
      open inductive Natt | OO | SS Nat
      def infix = {A : Type} (a b : A) => Type
      // Disambiguate by type checking
      def test (a : Nat) => a = 114514
      """).defs.isNotEmpty());
  }

  public record TyckResult(@NotNull ImmutableSeq<TyckDef> defs, @NotNull ResolveInfo info) { }

  public static TyckResult tyck(@Language("Aya") @NotNull String code) {
    var moduleLoader = SyntaxTestUtil.moduleLoader();
    var callback = new ModuleCallback<RuntimeException>() {
      ImmutableSeq<TyckDef> ok;
      @Override public void onModuleTycked(@NotNull ResolveInfo x, @NotNull ImmutableSeq<TyckDef> defs) { ok = defs; }
    };
    var info = moduleLoader.tyckModule(moduleLoader.resolve(SyntaxTestUtil.parse(code)), callback);
    return new TyckResult(callback.ok, info);
  }
}
