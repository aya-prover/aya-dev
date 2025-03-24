// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.normalize.Normalizer;
import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleCallback;
import org.aya.syntax.SyntaxTestUtil;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.call.FnCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.literate.CodeOptions.NormalizeMode;
import org.aya.util.TimeUtil;
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

/// Do NOT add simple test fixtures here.
/// Find TyckTest.aya and add tests there.
public class TyckTest {
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

  @Test
  public void tailrec() {
    var result = tyck("""
      tailrec def foo {A : Type} (a : A): A => a
      """).defs;
    assertTrue(result.isNotEmpty());
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
    FnDefLike tree_sortNat = getDef(defs, "tree_sortNat");

    var NatCall = new DataCall(Nat, 0, ImmutableSeq.empty());
    var ListNatCall = new DataCall(List, 0, ImmutableSeq.of(NatCall));

    IntFunction<Term> mkInt = i -> new IntegerTerm(i, O, S, NatCall);

    Function<ImmutableIntSeq, Term> mkList = xs -> new ListTerm(xs.mapToObj(mkInt), nil, cons, ListNatCall);

    var seed = 114514L;
    var random = new Random(seed);
    var largeList = mkList.apply(ImmutableIntSeq.fill(50, () -> random.nextInt(400)));
    var term = new FnCall(tree_sortNat, 0, ImmutableSeq.of(largeList));

    var normalizer = new Normalizer(new TyckState(result.info().shapeFactory(), new PrimFactory()));
    var sortResult = new Object() {
      Term t;
    };
    var deltaTime = TimeUtil.profile(() -> sortResult.t = normalizer
      .normalize(term, NormalizeMode.FULL));
    assertNotNull(sortResult.t);

    System.out.println("Done in " + TimeUtil.millisToString(deltaTime));
    System.out.println(sortResult.t.easyToString());

    TimeUtil.profileMany("Running many times on the same input...", 10, () ->
      normalizer.normalize(term, NormalizeMode.FULL));
  }

  public record TyckResult(@NotNull ImmutableSeq<TyckDef> defs, @NotNull ResolveInfo info) {
    public TyckDef find(@NotNull String name) {
      return defs.find(x -> x.ref().name().equals(name)).get();
    }
  }

  public static TyckResult tyck(@Language("Aya") @NotNull String code) {
    var moduleLoader = SyntaxTestUtil.moduleLoader();
    var callback = new ModuleCallback<RuntimeException>() {
      ImmutableSeq<TyckDef> ok;
      @Override
      public void onModuleTycked(@NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<TyckDef> defs) { ok = defs; }
    };
    var info = moduleLoader.tyckModule(moduleLoader.resolve(SyntaxTestUtil.parse(code)), callback);
    return new TyckResult(callback.ok, info);
  }
}
