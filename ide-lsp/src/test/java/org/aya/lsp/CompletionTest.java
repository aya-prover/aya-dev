// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.tree.TokenSet;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.function.IntIntBiFunction;
import kala.function.TriConsumer;
import org.aya.generic.AyaDocile;
import org.aya.ide.action.Completion;
import org.aya.ide.action.ContextWalker;
import org.aya.ide.action.ContextWalker2;
import org.aya.ide.action.NodeWalker;
import org.aya.ide.util.XY;
import org.aya.intellij.GenericNode;
import org.aya.lsp.actions.CompletionProvider;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.AyaParserImpl;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.ThrowingReporter;
import org.javacs.lsp.CompletionItemKind;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.aya.lsp.LspTest.*;
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

  private static final @NotNull Path COMPLETION_TEST_FILE = RES_DIR.resolve("CompletionTest.aya");

  private @NotNull SourceFile readTestFile() throws IOException {
    var content = Files.readString(COMPLETION_TEST_FILE);
    return new SourceFile(COMPLETION_TEST_FILE.getFileName().toString(), COMPLETION_TEST_FILE, content);
  }

  private @NotNull GenericNode<?> parseFile(@NotNull SourceFile file) {
    return new AyaParserImpl(new ThrowingReporter(AyaPrettierOptions.debug()))
      .parseNode(file.sourceCode());
  }

  @Test
  public void testNodeWalker() throws IOException {
    var file = readTestFile();
    var node = parseFile(file);
    Function<XY, NodeWalker.Result> runner = (xy) ->
      NodeWalker.run(file, node, xy, TokenSet.EMPTY);

    BiConsumer<XY, NodeWalker.Result> checker = (xy, result) -> {
      var mNode = result.node();
      var offset = result.offsetInNode();
      var lineColumn = SourcePos.offsetToLineColumn(file, mNode.range().getStartOffset(), 0);
      assertEquals(xy.x(), lineColumn.line + 1);
      assertEquals(xy.y(), lineColumn.column + offset);

      System.out.println("[PASS] position=" + xy + ", text=" + mNode.tokenText());
    };

    var cases = ImmutableSeq.of(
      new XY(3, 28),          // {b : Nat} _Nat : Nat
      new XY(3, 29),          // {b : Nat} N_at : Nat
      new XY(3, 31)           // {b : Bat} Nat_ : Nat
    );

    cases.forEach(xy -> checker.accept(xy, runner.apply(xy)));
  }

  @Test
  public void testRefocus() throws IOException {
    var sourceFile = readTestFile();
    var node = parseFile(sourceFile);
    Function<XY, GenericNode<?>> runner = (xy) -> {
      var mNode = NodeWalker.run(sourceFile, node, xy, TokenSet.EMPTY);
      return NodeWalker.refocus(mNode.node(), mNode.offsetInNode());
    };

    TriConsumer<XY, XY, GenericNode<?>> checker = (pos, expected, mNode) -> {
      var offset = mNode.range().getStartOffset();
      var lc = SourcePos.offsetToLineColumn(sourceFile, offset, 0);
      var actualXY = new XY(lc.line + 1, lc.column);
      assertEquals(expected, actualXY, pos.toString());
    };

    var cases = ImmutableMap.<XY, XY>of(
      new XY(1, 35), new XY(1, 34),      // suc (n : Nat) _| zero
      new XY(3, 13), new XY(3, 12),      // (a : _Nat)
      new XY(3, 14), new XY(3, 13),      // (a : N_at)
      new XY(4, 11), new XY(4, 11),      // => b_
      new XY(5, 48), new XY(5, 48),      // c (foo a)_
      new XY(6, 0), new XY(5, 48)        // c (foo a)\n_
    );

    cases.forEach((pos, expected) -> {
      checker.accept(pos, expected, runner.apply(pos));
    });
  }

  @Test
  public void testCompletion2() throws IOException {
    var sourceFile = readTestFile();
    var node = parseFile(sourceFile);
    Consumer<XY> runner = (xy) -> {
      var mNode = NodeWalker.run(sourceFile, node, xy, TokenSet.EMPTY);
      var focused = NodeWalker.refocus(mNode.node(), mNode.offsetInNode());
      System.out.println(xy + ": focus on " + focused);
      var walker = new ContextWalker2();
      walker.visit(focused);
      System.out.println(walker.location());
    };

    var cases = ImmutableSeq.of(
      new XY(1, 25),  // suc _(n : Nat)
      new XY(1, 35),      // ) _| zero
      new XY(1, 41),      // | zero_
      new XY(3, 28),      // : Nat} _Nat
      new XY(3, 34),      // : _Nat
      new XY(5, 36),      // suc d _in
      new XY(5, 48),      // c (foo a)_
      new XY(9, 10),      // b <- bar_,
      new XY(10, 2)       // _c
    );

    cases.forEach(runner);
  }
}
