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
import org.aya.syntax.literate.CodeOptions.NormalizeMode;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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

  @Test public void elimResolve() {
    assertTrue(tyck("""
      open inductive Nat | O | S Nat
      open inductive Phantom Nat Nat (A : Type) | mk A
      variable a b : Nat
      def plus : Phantom a b Nat elim a
      | O => mk b
      | S a => mk b
      """).defs.isNotEmpty());
  }

  @Test public void classTyck() {
    // 🦀
    assertTrue(tyck("""
      prim I prim Path prim coe
      variable A : Type
      def infix = (a b : A) => Path (\\i => A) a b
      
      class Monoid
      | classifying carrier : Type
      | unit : carrier
      | infix * : carrier -> carrier -> carrier
        tighter =
      | idl (x : carrier) : unit * x = x
      """).defs.isNotEmpty());
  }

  @Test
  public void what() {
    assertTrue(tyck("""
      class Kontainer
      | Taipe : Type
      | walue : Taipe
      """).defs.isNotEmpty());
  }

  @SuppressWarnings("unchecked") private static <T extends AnyDef> T
  getDef(@NotNull ImmutableSeq<TyckDef> defs, @NotNull String name) {
    return (T) TyckAnyDef.make(defs.find(x -> x.ref().name().equals(name)).get());
  }

  @Test public void sort() throws IOException {
    var result = tyck(Files.readString(Paths.get("../jit-compiler/src/test/resources/TreeSort.aya")));

    var defs = result.defs;

    DataDefLike Nat = getDef(defs, "Nat");
    ConDefLike O = getDef(defs, "O");
    ConDefLike S = getDef(defs, "S");
    DataDefLike List = getDef(defs, "List");
    ConDefLike nil = getDef(defs, "[]");
    ConDefLike cons = getDef(defs, ":>");
    FnDefLike le = getDef(defs, "le");
    FnDefLike tree_sort = getDef(defs, "tree_sort");

    var NatCall = new DataCall(Nat, 0, ImmutableSeq.empty());
    var ListNatCall = new DataCall(List, 0, ImmutableSeq.of(NatCall));

    IntFunction<Term> mkInt = i -> new IntegerTerm(i, O, S, NatCall);

    Function<ImmutableIntSeq, Term> mkList = xs -> new ListTerm(xs.mapToObj(mkInt), nil, cons, ListNatCall);

    var leCall = new LamTerm(new Closure.Jit(x ->
      new LamTerm(new Closure.Jit(y ->
        new FnCall(le, 0, ImmutableSeq.of(x, y))))));

    var seed = 114514L;
    var random = new Random(seed);
    var largeList = mkList.apply(ImmutableIntSeq.fill(50, () -> Math.abs(random.nextInt()) % 100));
    var args = ImmutableSeq.of(NatCall, leCall, largeList);

    var beginTime = System.currentTimeMillis();
    var sortResult = new Normalizer(new TyckState(result.info().shapeFactory(), new PrimFactory()))
      .normalize(new FnCall(tree_sort, 0, args), NormalizeMode.FULL);
    var endTime = System.currentTimeMillis();
    assertNotNull(sortResult);

    System.out.println(STR."Done in \{(endTime - beginTime)}");
    System.out.println(sortResult.debuggerOnlyToString());
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
