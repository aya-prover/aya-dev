// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import com.intellij.openapi.util.text.Strings;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.AyaDocile;
import org.aya.ide.action.Completion;
import org.aya.ide.action.ContextWalker;
import org.aya.ide.action.NodeWalker;
import org.aya.ide.util.XY;
import org.aya.lsp.actions.CompletionProvider;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.AyaParserImpl;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.javacs.lsp.CompletionItemKind;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.aya.lsp.LspTest.TEST_LIB;
import static org.aya.lsp.LspTest.launch;
import static org.aya.lsp.tester.TestCommand.compile;
import static org.junit.jupiter.api.Assertions.*;

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
    var inResult = new XY(13, 35);        // : _Nat
    var inLetTele = new XY(14, 36);       // (e : _Nat)
    var inLetResult = new XY(14, 43);     // : _Nat
    var inLetBody = new XY(14, 55);       // _c a a
    var inSucClause = new XY(15, 34);     // "114" in _a
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
    var xy = new XY(14, 55);        // _c a a
    // XY: 14, 52
    var list = ImmutableSeq.from(CompletionProvider.completion(source, xy, AyaDocile::easyToString).items);

    var c = list.find(i -> i.label.equals("c")).get();
    assertEquals(CompletionItemKind.Variable, c.kind);
    assertNotNull(c.labelDetails);
    assertEquals(" : Nat -> Nat -> Nat", c.labelDetails.detail);

    var String = list.find(i -> i.label.equals("String")).get();
    assertEquals(CompletionItemKind.Interface, String.kind);
    assertNotNull(String.labelDetails);
    assertEquals("StringPrims", String.labelDetails.description);
  }

  public void assertContext(@NotNull ContextWalker walker, String @NotNull ... asserts) {
    walker.localContext().forEachWith(ImmutableArray.Unsafe.wrap(asserts), (actual, expected) ->
      Assertions.assertEquals(expected, actual.easyToString()));
  }

  @Test
  public void testNodeWalker() throws IOException {
    var file = TEST_LIB.resolve("src").resolve("HelloWorld.aya");
    var content = Files.readString(file);
    var node = new AyaParserImpl(new ThrowingReporter(AyaPrettierOptions.debug()))
      .parseNode(Strings.convertLineSeparators(content));
    var sourceFile = new SourceFile("HelloWorld.aya", file, content);

    var target = new NodeWalker(sourceFile, node, new XY(13, 25)).run();
    System.out.println(target);
  }
}
