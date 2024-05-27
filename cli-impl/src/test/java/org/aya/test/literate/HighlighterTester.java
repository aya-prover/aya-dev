// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.literate;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.primitive.IntTuple2;
import org.aya.cli.literate.HighlightInfo;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Link;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.test.literate.HighlighterTester.ExpectedHighlightType.Keyword;
import org.aya.test.literate.HighlighterTester.ExpectedHighlightType.LitInt;
import org.aya.test.literate.HighlighterTester.ExpectedHighlightType.LitString;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class HighlighterTester {
  public record ExpectedHighlightInfo(
    @NotNull SourcePos sourcePos,
    @NotNull ExpectedHighlightType expected
  ) implements Comparable<ExpectedHighlightInfo> {
    @Override public int compareTo(@NotNull HighlighterTester.ExpectedHighlightInfo o) {
      return sourcePos.compareTo(o.sourcePos);
    }
  }

  public sealed interface ExpectedHighlightType {
    @NotNull String display();

    record Def(
      @Override @NotNull String display, @Nullable String name,
      @Nullable HighlightInfo.DefKind kind
    ) implements ExpectedHighlightType { }

    record Ref(
      @Override @NotNull String display, @Nullable String name,
      @Nullable HighlightInfo.DefKind kind
    ) implements ExpectedHighlightType { }

    record Keyword(@Override @NotNull String display) implements ExpectedHighlightType { }

    record LitString(@NotNull String expected) implements ExpectedHighlightType {
      @Override public @NotNull String display() { return '\"' + expected + '\"'; }
    }

    record LitInt(int expected) implements ExpectedHighlightType {
      @Override public @NotNull String display() { return Integer.toString(expected); }
    }
  }

  public final @NotNull String sourceCode;
  public final @NotNull ImmutableSeq<HighlightInfo> actual;

  // NotNull array with Nullable element
  public final ExpectedHighlightInfo[] expected;

  // TODO[hoshino]: Inductive Defined (allow scope)
  // (User-Defined Name, (Unique ID, Nullable Def Kind))
  public final MutableMap<String, Tuple2<Link, Option<HighlightInfo.DefKind>>> defMap = MutableMap.create();
  public final MutableMap<Link, Option<HighlightInfo.DefKind>> defSet = MutableMap.create();

  public HighlighterTester(@NotNull String sourceCode, @NotNull ImmutableSeq<HighlightInfo> actual, @Nullable ExpectedHighlightInfo[] expected) {
    this.sourceCode = sourceCode;
    this.actual = actual;
    this.expected = expected;
  }

  public void runTest() {
    var sortedActual = actual.view().distinct().sorted().toImmutableSeq().view();
    if (sortedActual.getLast() instanceof HighlightInfo.Lit(_, var kind) && kind == HighlightInfo.LitKind.Eol)
      // Remove the last Eol
      sortedActual = sortedActual.dropLast(1);
    runTest(sortedActual.toImmutableSeq(), Seq.of(expected));
  }

  public void runTest(@NotNull ImmutableSeq<HighlightInfo> actuals, @NotNull Seq<ExpectedHighlightInfo> expecteds) {
    actuals.forEachWith(expecteds, (actual, expected) -> {
      if (expected == null) {
        switch (actual) {
          case HighlightInfo.Def def -> checkDef(actual.sourcePos(), def);
          case HighlightInfo.Ref ref -> checkRef(actual.sourcePos(), ref);
          default -> { }
        }

        return;
      }

      assertEquals(
        IntTuple2.of(expected.sourcePos.tokenStartIndex(), expected.sourcePos.tokenEndIndex()),
        IntTuple2.of(actual.sourcePos().tokenStartIndex(), actual.sourcePos().tokenEndIndex()));

      var sourcePos = actual.sourcePos();
      var expectedText = expected.expected.display();
      var actualText = sourceCode.substring(sourcePos.tokenStartIndex(), sourcePos.tokenEndIndex() + 1);

      assertEquals(expectedText, actualText, STR."at \{sourcePos}");

      switch (actual) {
        case HighlightInfo.Lit(_, var ty)
          when ty == HighlightInfo.LitKind.Keyword && expected.expected() instanceof Keyword -> {
        }
        case HighlightInfo.Lit(_, var ty)
          when ty == HighlightInfo.LitKind.Int && expected.expected() instanceof LitInt -> {
        }
        case HighlightInfo.Lit(_, var ty)
          when ty == HighlightInfo.LitKind.String && expected.expected() instanceof LitString -> {
        }

        case HighlightInfo.Def def
          when expected.expected() instanceof ExpectedHighlightType.Def expectedDef ->
          assertDef(sourcePos, def, expectedDef);

        case HighlightInfo.Ref ref
          when expected.expected() instanceof ExpectedHighlightType.Ref expectedRef ->
          assertRef(sourcePos, ref, expectedRef);

        case HighlightInfo.Err _ -> throw new UnsupportedOperationException("TODO");   // TODO

        default ->
          fail(STR."expected: \{expected.getClass().getSimpleName()}, but actual: \{actual.getClass().getSimpleName()}");
      }
    });
    assertEquals(expecteds.size(), actuals.size(), "size mismatch");
  }

  /**
   * Check no duplicated def.
   */
  public void checkDef(@NotNull SourcePos sourcePos, @NotNull HighlightInfo.Def def) {
    var existDef = defSet.containsKey(def.target());
    assertFalse(existDef, STR."Duplicated def: \{def.target()} at \{sourcePos}");

    defSet.put(def.target(), Option.ofNullable(def.kind()));
  }

  public void assertDef(@NotNull SourcePos sourcePos, @NotNull HighlightInfo.Def actualDef, @NotNull HighlighterTester.ExpectedHighlightType.Def expectedDef) {
    checkDef(sourcePos, actualDef);

    assertEquals(expectedDef.kind(), actualDef.kind());

    var name = expectedDef.name();

    if (name != null) {
      var existName = defMap.getOption(name);
      assertFalse(existName.isDefined(), STR."Duplicated name: \{expectedDef.name()}");

      defMap.put(name, Tuple.of(actualDef.target(), Option.ofNullable(actualDef.kind())));
    }
  }

  /**
   * Check the reference
   */
  public void checkRef(@NotNull SourcePos sourcePos, @NotNull HighlightInfo.Ref ref) {
    var defData = defSet.getOrNull(ref.target());

    assertNotNull(defData, STR."Expected def: \{ref.target()} at \{sourcePos}");
    assertEquals(defData.getOrNull(), ref.kind());
  }

  public void assertRef(@NotNull SourcePos sourcePos, @NotNull HighlightInfo.Ref actualRef, @NotNull HighlighterTester.ExpectedHighlightType.Ref expectedRef) {
    checkRef(sourcePos, actualRef);

    var name = expectedRef.name();

    if (name != null) {
      var existName = defMap.getOption(name);
      assertTrue(existName.isDefined(), STR."Undefined name: \{expectedRef.name()}");

      var defData = existName.get();
      assertEquals(defData.component2().getOrNull(), actualRef.kind());
    }

    assertEquals(actualRef.kind(), expectedRef.kind());
  }

  public static void highlightAndTest(@Language("Aya") @NotNull String code, @Nullable ExpectedHighlightInfo... expected) {
    var sourceFile = new SourceFile("test.aya", Option.none(), code);
    var reporter = new ThrowingReporter(AyaPrettierOptions.pretty());
    var moduleLoader = new DumbModuleLoader(new EmptyContext(reporter, Path.of(".")).derive("main"));
    var stmts = new AyaParserImpl(reporter).program(sourceFile);
    moduleLoader.resolve(stmts);
    var result = SyntaxHighlight.highlight(null, Option.some(sourceFile), stmts)
      .filterNot(it -> it instanceof HighlightInfo.Lit(_, var kind)
        && ignored.contains(kind));
    new HighlighterTester(code, result, expected).runTest();
  }

  static Seq<HighlightInfo.LitKind> ignored = Seq.of(HighlightInfo.LitKind.Eol, HighlightInfo.LitKind.Whitespace);

  /// region Helper

  private static @NotNull SourcePos mockPos(int start, int end) {
    return new SourcePos(SourceFile.NONE, start, end, 0, 0, 0, 0);
  }

  public static @NotNull ExpectedHighlightInfo keyword(int begin, int end, @NotNull String display) {
    return new ExpectedHighlightInfo(mockPos(begin, end), new Keyword(display));
  }

  public static @NotNull ExpectedHighlightInfo def(int begin, int end, @NotNull String display, @Nullable String name, @Nullable HighlightInfo.DefKind defKind) {
    return new ExpectedHighlightInfo(mockPos(begin, end), new ExpectedHighlightType.Def(display, name, defKind));
  }

  public static @NotNull ExpectedHighlightInfo def(int begin, int end, @NotNull String display, @Nullable HighlightInfo.DefKind defKind) {
    return def(begin, end, display, null, defKind);
  }

  public static @NotNull ExpectedHighlightInfo localDef(int begin, int end, @NotNull String display, @Nullable String name) {
    return def(begin, end, display, name, HighlightInfo.DefKind.LocalVar);
  }

  public static @NotNull ExpectedHighlightInfo localDef(int begin, int end, @NotNull String display) {
    return localDef(begin, end, display, null);
  }

  public static @NotNull ExpectedHighlightInfo ref(int begin, int end, @NotNull String display, @Nullable String name, HighlightInfo.DefKind defKind) {
    return new ExpectedHighlightInfo(mockPos(begin, end), new ExpectedHighlightType.Ref(display, name, defKind));
  }

  public static @NotNull ExpectedHighlightInfo ref(int begin, int end, @NotNull String display, HighlightInfo.DefKind defKind) {
    return ref(begin, end, display, null, defKind);
  }

  public static @NotNull ExpectedHighlightInfo localRef(int begin, int end, @NotNull String display, @Nullable String name) {
    return ref(begin, end, display, name, HighlightInfo.DefKind.LocalVar);
  }

  public static @NotNull ExpectedHighlightInfo localRef(int begin, int end, @NotNull String display) {
    return localRef(begin, end, display, null);
  }

  public static @NotNull ExpectedHighlightInfo litInt(int begin, int end, int display) {
    return new ExpectedHighlightInfo(mockPos(begin, end), new LitInt(display));
  }

  @Contract(" -> null")
  public static @Nullable ExpectedHighlightInfo whatever() { return null; }
  /// endregion
}
