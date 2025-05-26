// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.AyaDocile;
import org.aya.ide.action.Completion;
import org.aya.ide.action.ContextWalker;
import org.aya.ide.util.XY;
import org.aya.lsp.actions.CompletionProvider;
import org.aya.syntax.concrete.stmt.Stmt;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.aya.lsp.LspTest.TEST_LIB;
import static org.aya.lsp.LspTest.launch;
import static org.aya.lsp.tester.TestCommand.compile;

public class CompletionTest {
  private static @NotNull ContextWalker runWalker(@NotNull ImmutableSeq<Stmt> stmts, @NotNull XY xy) {
    var walker = new ContextWalker(xy);
    stmts.forEach(walker);
    return walker;
  }

  @Test public void testContextWalker() {
    var client = launch(TEST_LIB);
    client.execute(compile((_, _) -> { }));
    var source = client.service.find(TEST_LIB.resolve("src").resolve("HelloWorld.aya"));
    assert source != null;
    var stmt = source.program().get();
    assert stmt != null;

    var inTelescope = new XY(13, 25);     // {b : _Nat}
    var inResult = new XY(13, 32);        // : _Nat
    var inLetTele = new XY(14, 33);       // (e : _Nat)
    var inLetResult = new XY(14, 40);     // : _Nat
    var inLetBody = new XY(14, 52);       // _c a a
    var inSucClause = new XY(15, 31);     // "114" in _a
    var inSucPat = new XY(15, 4);         // | _suc a
    var betweenParams = new XY(13, 20);   // (a : Nat) _{b : Nat}

    var result0 = runWalker(stmt, inTelescope);   // (a : Nat)
    var result1 = runWalker(stmt, inResult);      // (a : Nat) {b : Nat}
    var result2 = runWalker(stmt, inLetTele);     // (a : Nat) {b : Nat} (d : Nat)
    var result3 = runWalker(stmt, inLetResult);   // (a : Nat) {b : Nat} (d : Nat) (e : Nat)
    var result4 = runWalker(stmt, inLetBody);     // (a : Nat) {b : Nat} (c : Nat -> Nat -> Nat)
    var result5 = runWalker(stmt, inSucClause);   // (a : Nat) (b : String)
    var result6 = runWalker(stmt, inSucPat);
    var result7 = runWalker(stmt, betweenParams); // (a : Nat)

    assertContext(result0, "a : Nat");
    assertContext(result1, "a : Nat", "b : Nat");
    assertContext(result2, "a : Nat", "b : Nat", "d : Nat");
    assertContext(result3, "a : Nat", "b : Nat", "d : Nat", "e : Nat");
    assertContext(result4, "a : Nat", "b : Nat", "c : Nat -> Nat -> Nat");
    assertContext(result5, "a : Nat", "b");     // no type annotation, we can't infer type at resolving stage
    assertContext(result7, "a : Nat");
  }

  @Test public void testModuleContextExtraction() {
    var client = launch(TEST_LIB);
    client.execute(compile((_, _) -> { }));
    var source = client.service.find(TEST_LIB.resolve("src").resolve("HelloWorld.aya"));
    assert source != null;
    var info = source.resolveInfo().get();
    assert info != null;
    var result = Completion.resolveTopLevel(info.thisModule());
    return;
  }

  @Test public void testCompletionProvider() {
    // TODO: check by human eyes, sorry
    var client = launch(TEST_LIB);
    client.execute(compile((_, _) -> { }));
    var source = client.service.find(TEST_LIB.resolve("src").resolve("HelloWorld.aya"));
    assert source != null;
    var xy = new XY(14, 52);        // _c a a
    // XY: 14, 52
    var list = CompletionProvider.completion(source, xy, AyaDocile::easyToString).items;
    return;
  }

  public void assertContext(@NotNull ContextWalker walker, String @NotNull ... asserts) {
    walker.localContext().forEachWith(ImmutableArray.Unsafe.wrap(asserts), (actual, expected) ->
      Assertions.assertEquals(expected, actual.easyToString()));
  }
}
