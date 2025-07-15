// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.tree.TokenSet;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import kala.function.IntIntBiFunction;
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
import org.aya.util.reporter.ThrowingReporter;
import org.javacs.lsp.CompletionItemKind;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

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

  private static final @NotNull Path TEST_FILE = TEST_LIB.resolve("src").resolve("HelloWorld.aya");

  private @NotNull SourceFile readTestFile() throws IOException {
    var content = Files.readString(TEST_FILE);
    return new SourceFile(TEST_FILE.getFileName().toString(), TEST_FILE, content);
  }

  private @NotNull GenericNode<?> parseFile(@NotNull SourceFile file) {
    return new AyaParserImpl(new ThrowingReporter(AyaPrettierOptions.debug()))
      .parseNode(file.sourceCode());
  }


  @Test
  public void testNodeWalker() throws IOException {
    var file = TEST_LIB.resolve("src").resolve("HelloWorld.aya");
    var content = Files.readString(file);
    var node = new AyaParserImpl(new ThrowingReporter(AyaPrettierOptions.debug()))
      .parseNode(Strings.convertLineSeparators(content));
    var sourceFile = new SourceFile("HelloWorld.aya", file, content);

    var target = NodeWalker.run(sourceFile, node, new XY(13, 25), TokenSet.EMPTY);
    System.out.println(target);
  }

  @Test
  public void testRefocus() throws IOException {
    var sourceFile = readTestFile();
    var node = parseFile(sourceFile);
    IntIntBiFunction<GenericNode<?>> runner = (x, y) -> {
      var xy = new XY(x, y);
      var mNode = NodeWalker.run(sourceFile, node, xy, TokenSet.EMPTY);
      var focused = NodeWalker.refocus(mNode);
      return focused;
    };

    var afterClause2 = runner.apply(15, 35);    // in a_'\n'
    var onClause2Bar = runner.apply(15, 2);     // _| suc a
    var betweenParam = runner.apply(13, 19);    // (a : Nat)_ {b : Nat}
    var onId = runner.apply(13, 15);      // (a : _Nat)

    // TODO: how to make assertion? use offset??
    System.out.println(afterClause2);
    System.out.println(onClause2Bar);
    System.out.println(betweenParam);
    System.out.println(onId);
  }

  @Test
  public void testCompletion2() throws IOException {
    var sourceFile = readTestFile();
    var node = parseFile(sourceFile);
    Consumer<XY> runner = (xy) -> {
      var mNode = NodeWalker.run(sourceFile, node, xy, TokenSet.EMPTY);
      var focused = NodeWalker.refocus(mNode);
      var walker = new ContextWalker2();
      walker.visit(focused);
      System.out.println(walker.location());
    };

    runner.accept(new XY(13, 25));    // {b : _Nat}
    runner.accept(new XY(14, 60));    // c a a_
  }
}
