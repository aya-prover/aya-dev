// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import com.intellij.psi.tree.TokenSet;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.function.TriConsumer;
import org.aya.generic.AyaDocile;
import org.aya.ide.action.Completion;
import org.aya.ide.action.completion.BindingInfoExtractor;
import org.aya.ide.action.completion.ContextWalker;
import org.aya.ide.action.completion.NodeWalker;
import org.aya.ide.util.XY;
import org.aya.intellij.GenericNode;
import org.aya.lsp.actions.CompletionProvider;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.AyaParserImpl;
import org.aya.producer.AyaProducer;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.ThrowingReporter;
import org.javacs.lsp.CompletionItemKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.aya.lsp.LspTest.*;
import static org.aya.lsp.tester.TestCommand.compile;
import static org.junit.jupiter.api.Assertions.*;

public class CompletionTest {
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

  @Test public void testCompletionProvider() throws IOException {
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
      return NodeWalker.refocus(mNode);
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

    var producer = new AyaProducer(
      Either.left(sourceFile),
      new ThrowingReporter(AyaPrettierOptions.debug())
    );

    var stmts = producer.program(node);
    assert stmts.isLeft();

    var extractor = new BindingInfoExtractor()
      .accept(stmts.getLeftValue());

    Function<XY, ContextWalker> runner = (xy) -> {
      var mNode = NodeWalker.run(sourceFile, node, xy, TokenSet.EMPTY);
      var focused = NodeWalker.refocus(mNode);
      System.out.println(xy + ": focus on " + focused);
      var walker = new ContextWalker(extractor.bindings(), extractor.modules());
      walker.visit(focused);
      System.out.println(walker.location());
      return walker;
    };

    var case0 = runner.apply(new XY(1, 25));      // suc _(n : Nat)
    var case1 = runner.apply(new XY(1, 35));      // ) _| zero
    var case2 = runner.apply(new XY(1, 41));      // | zero_
    var case3 = runner.apply(new XY(3, 28));      // : Nat} _Nat
    var case4 = runner.apply(new XY(3, 34));      // : _Nat
    var case5 = runner.apply(new XY(5, 36));      // suc d _in
    var case6 = runner.apply(new XY(5, 48));      // c (foo a)_
    var case6p1 = runner.apply(new XY(5, 50));    // c (foo a)  _     // the cursor is at an impossible position
    var case7 = runner.apply(new XY(9, 10));      // b <- bar_,
    var case8 = runner.apply(new XY(10, 2));      // _c
    var case9 = runner.apply(new XY(24, 10));     // fn b => _a + b
    var case10 = runner.apply(new XY(28, 10));    // | c := a_
    var case11 = runner.apply(new XY(13, 20));    // [ a + b_ |
    var case12 = runner.apply(new XY(13, 37));    // b <- d_ ]

    assertContext2(case0);
    assertContext2(case1, "n : Nat");
    assertContext2(case2);
    assertContext2(case3, "a : Nat", "b : Nat");
    assertContext2(case4, "a : Nat", "b : Nat");
    assertContext2(case5, "d : Nat", "suc : _", "a : _", "b : Nat");
    assertContext2(case6, "c : Nat -> _", "suc : _", "a : _", "b : Nat");
    assertContext2(case6p1, "c : Nat -> _", "suc : _", "a : _", "b : Nat");
    assertContext2(case9, "b : _", "a : Nat");
    assertContext2(case10, "a : _");
    assertContext2(case11, "a : _", "b : _");
    assertContext2(case12, "a : _");
  }

  private void assertContext2(@NotNull ContextWalker walker, @NotNull String... expected) {
    var actuals = walker.localContext()
      .filter(it -> it.var() instanceof LocalVar);

    SeqView<@Nullable String> expecteds = ImmutableSeq.from(expected)
      .view()
      .concat(ImmutableSeq.fill(114514, (String) null));

    actuals.forEachWith(expecteds, (a, e) -> {
      var lvar = (LocalVar) a.var();
      String actual = lvar.isGenerated()
        ? a.type().easyToString()
        : a.easyToString();

      assertNotNull(e, "Unexpected: '" + actual + "'");
      assertEquals(e, actual);
    });
  }
}
