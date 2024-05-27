// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.normalize.Normalizer;
import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleCallback;
import org.aya.syntax.SyntaxTestUtil;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.call.FnCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.literate.CodeOptions;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TyckTest {
  @Test public void test0() {
    var result = tyck("""
      data Nat | O | S Nat
      data FreeMonoid (A : Type) | e | cons A (FreeMonoid A)
      
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
      open data Unit | unit
      variable A : Type
      def foo {value : A} : A => value
      def what : Unit => foo {value := unit}
      """).defs;
    assertTrue(result.isNotEmpty());
  }

  @Test public void path0() {
    var result = tyck("""
      data Nat
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
      data Nat | O | S Nat
      prim I : ISet
      prim Path (A : I -> Type) (a : A 0) (b : A 1) : Type
      prim coe
      variable A : Type
      def infix = (a b : A) => Path (\\i => A) a b
      def refl {a : A} : a = a => \\i => a
      open data Int
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
      open data Unit | unit
      data Nat | O | S Nat
      open data SomeDT Nat
      | m => someDT
      def how' {m : Nat} (a : Nat) (b : SomeDT m) : Nat => 0
      def what {A : Nat -> Type} (B : Fn (n : Nat) -> A n -> Nat) : Unit => unit
      def boom => what (fn n => fn m => how' 0 m)
      """).defs;
    assertTrue(result.isNotEmpty());
  }

  @Test public void test2() {
    var result = tyck("""
      open data Nat | O | S Nat
      open data Bool | true | false
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
      open data Nat | O | S Nat
      open data Natt | OO | SS Nat
      def infix = {A : Type} (a b : A) => Type
      // Disambiguate by type checking
      def test (a : Nat) => a = 114514
      """).defs.isNotEmpty());
  }

  @Test
  public void sort() {
    var result = tyck("""
      open data Nat | O | S Nat
      open data Bool | True | False
      open data List Type
      | nil
      | A => infixr cons A (List A)
            
      open data Color | red | black
      def Decider (A : Type) => Fn (x y : A) -> Bool
            
      variable A : Type
            
      open data RBTree (A : Type) : Type
      | rbLeaf
      | rbNode Color (RBTree A) A (RBTree A)
            
      def rbTreeToList (rb : RBTree A) (r : List A) : List A elim rb
      | rbLeaf => r
      | rbNode x t1 a t2 => rbTreeToList t1 (a cons rbTreeToList t2 r)
            
      def repaint (RBTree A) : RBTree A
      | rbNode c l a r => rbNode black l a r
      | rbLeaf => rbLeaf

      def le (x y : Nat) : Bool
      | O, _ => True
      | S _, O => False
      | S a, S b => le a b
            
      def balanceLeft Color (RBTree A) A (RBTree A) : RBTree A
      | black, rbNode red (rbNode red a x b) y c, v, r =>
          rbNode red (rbNode black a x b) y (rbNode black c v r)
      | black, rbNode red a x (rbNode red b y c), v, r =>
          rbNode red (rbNode black a x b) y (rbNode black c v r)
      | c, a, v, r => rbNode c a v r
            
      def balanceRight Color (RBTree A) A (RBTree A) : RBTree A
      | black, l, v, rbNode red (rbNode red b y c) z d =>
          rbNode red (rbNode black l v b) y (rbNode black c z d)
      | black, l, v, rbNode red b y (rbNode red c z d) =>
          rbNode red (rbNode black l v b) y (rbNode black c z d)
      | c, l, v, b => rbNode c l v b

      def insert_lemma (dec_le : Decider A) (a a1 : A) (c : Color) (l1 l2 : RBTree A) (b : Bool) : RBTree A elim b
      | True => balanceRight c l1 a1 (insert a l2 dec_le)
      | False => balanceLeft c (insert a l1 dec_le) a1 l2

      def insert (a : A) (node : RBTree A) (dec_le : Decider A) : RBTree A elim node
      | rbLeaf => rbNode red rbLeaf a rbLeaf
      | rbNode c l1 a1 l2 => insert_lemma dec_le a a1 c l1 l2 (dec_le a1 a)

      private def aux (l : List A) (r : RBTree A) (dec_le : Decider A) : RBTree A elim l
      | nil => r
      | a cons l => aux l (repaint (insert a r dec_le)) dec_le
      def tree_sort (dec_le : Decider A) (l : List A) => rbTreeToList (aux l rbLeaf dec_le) nil
      """);

    var defs = result.defs;

    var Nat = (DataDefLike) TyckAnyDef.make(defs.find(x -> x.ref().name().equals("Nat")).get());
    var O = (ConDefLike) TyckAnyDef.make(defs.find(x -> x.ref().name().equals("O")).get());
    var S = (ConDefLike) TyckAnyDef.make(defs.find(x -> x.ref().name().equals("S")).get());
    var List = (DataDefLike) TyckAnyDef.make(defs.find(x -> x.ref().name().equals("List")).get());
    var nil = (ConDefLike) TyckAnyDef.make(defs.find(x -> x.ref().name().equals("nil")).get());
    var cons = (ConDefLike) TyckAnyDef.make(defs.find(x -> x.ref().name().equals("cons")).get());
    var le = (FnDefLike) TyckAnyDef.make(defs.find(x -> x.ref().name().equals("le")).get());
    var tree_sort = (FnDefLike) TyckAnyDef.make(defs.find(x -> x.ref().name().equals("tree_sort")).get());

    var NatCall = new DataCall(Nat, 0, ImmutableSeq.empty());
    var ListNatCall = new DataCall(List, 0, ImmutableSeq.of(NatCall));

    IntFunction<Term> mkInt = i -> new IntegerTerm(i, O, S, NatCall);

    Function<ImmutableIntSeq, Term> mkList = xs -> new ListTerm(xs.mapToObj(mkInt::apply), nil, cons, ListNatCall);

    var leCall = new LamTerm(new Closure.Jit(x ->
      new LamTerm(new Closure.Jit(y ->
        new FnCall(le, 0, ImmutableSeq.of(x, y))))));

    var seed = 114514L;
    var random = new Random(seed);
    var largeList = mkList.apply(ImmutableIntSeq.fill(50, () -> Math.abs(random.nextInt()) % 100));
    var args = ImmutableSeq.of(NatCall, leCall, largeList);

    var beginTime = System.currentTimeMillis();
    var sortResult = new Normalizer(new TyckState(result.info().shapeFactory(), new PrimFactory()))
      .normalize(new FnCall(tree_sort, 0, args), CodeOptions.NormalizeMode.FULL);
    var endTime = System.currentTimeMillis();
    assertNotNull(sortResult);

    System.out.println(STR."Done in \{(endTime - beginTime)}");
    System.out.println(sortResult.debuggerOnlyToString());
  }

  public record TyckResult(@NotNull ImmutableSeq<TyckDef> defs, @NotNull ResolveInfo info) {

  }

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
