// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.cli.HighlighterTester.ExpectedHighlightType.Def;
import org.aya.cli.HighlighterTester.ExpectedHighlightType.LitInt;
import org.aya.cli.HighlighterTester.ExpectedHighlightType.Ref;
import org.aya.cli.literate.HighlightInfo;
import org.aya.cli.literate.SyntaxHighlight;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.PrimDef.Factory;
import org.aya.pretty.doc.Link;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.EmptyModuleLoader;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("UnknownLanguage")
public class HighlighterTester {
  record ExpectedHighlightInfo(
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
    ) implements ExpectedHighlightType {
    }

    record Ref(
      @Override @NotNull String display, @Nullable String name,
      @Nullable HighlightInfo.DefKind kind
    ) implements ExpectedHighlightType {}

    record Keyword(@Override @NotNull String display) implements ExpectedHighlightType {}

    record LitString(@NotNull String expected) implements ExpectedHighlightType {
      @Override
      public @NotNull String display() {
        return '\"' + expected + '\"';
      }
    }

    record LitInt(int expected) implements ExpectedHighlightType {
      @Override
      public @NotNull String display() {
        return Integer.toString(expected);
      }
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
    runTest(actual.view().sorted().distinct().toImmutableSeq(), Seq.of(expected));
  }

  public void runTest(@NotNull Seq<HighlightInfo> actuals, @NotNull Seq<ExpectedHighlightInfo> expecteds) {
    assertEquals(actuals.size(), expecteds.size(), "size mismatch");
    for (var tup : actuals.zipView(expecteds)) {
      var actual = tup._1;
      var expected = tup._2;

      if (expected == null) {
        switch (actual.type()) {
          case HighlightInfo.SymDef def -> checkDef(actual.sourcePos(), def);
          case HighlightInfo.SymRef ref -> checkRef(actual.sourcePos(), ref);
          default -> {}
        }

        continue;
      }

      assertEquals(
        Tuple.of(expected.sourcePos.tokenStartIndex(), expected.sourcePos.tokenEndIndex()),
        Tuple.of(actual.sourcePos().tokenStartIndex(), actual.sourcePos().tokenEndIndex()));

      var sourcePos = actual.sourcePos();
      var expectedText = expected.expected.display();
      var actualText = sourceCode.substring(sourcePos.tokenStartIndex(), sourcePos.tokenEndIndex() + 1);

      assertEquals(expectedText, actualText, "at " + sourcePos);

      switch (actual.type()) {
        case HighlightInfo.SymLit(var ty)
          when ty == HighlightInfo.LitKind.Keyword && expected.expected() instanceof ExpectedHighlightType.Keyword -> {
        }
        case HighlightInfo.SymLit(var ty)
          when ty == HighlightInfo.LitKind.Int && expected.expected() instanceof LitInt -> {
        }
        case HighlightInfo.SymLit(var ty)
          when ty == HighlightInfo.LitKind.String && expected.expected() instanceof ExpectedHighlightType.LitString -> {
        }

        case HighlightInfo.SymDef def
          when expected.expected() instanceof Def expectedDef -> assertDef(sourcePos, def, expectedDef);

        case HighlightInfo.SymRef ref
          when expected.expected() instanceof Ref expectedRef -> assertRef(sourcePos, ref, expectedRef);

        case HighlightInfo.SymError error -> throw new UnsupportedOperationException("TODO");   // TODO

        default ->
          fail("expected: " + expected.getClass().getSimpleName() + ", but actual: " + actual.getClass().getSimpleName());
      }
    }
  }

  /**
   * Check no duplicated def.
   */
  public void checkDef(@NotNull SourcePos sourcePos, @NotNull HighlightInfo.SymDef def) {
    var existDef = defSet.containsKey(def.target());
    assertFalse(existDef, "Duplicated def: " + def.target() + " at " + sourcePos);

    defSet.put(def.target(), Option.ofNullable(def.kind()));
  }

  public void assertDef(@NotNull SourcePos sourcePos, @NotNull HighlightInfo.SymDef actualDef, @NotNull Def expectedDef) {
    checkDef(sourcePos, actualDef);

    assertEquals(expectedDef.kind(), actualDef.kind());

    var name = expectedDef.name();

    if (name != null) {
      var existName = defMap.getOption(name);
      assertFalse(existName.isDefined(), "Duplicated name: " + expectedDef.name());

      defMap.put(name, Tuple.of(actualDef.target(), Option.ofNullable(actualDef.kind())));
    }
  }

  /**
   * Check the reference
   */
  public void checkRef(@NotNull SourcePos sourcePos, @NotNull HighlightInfo.SymRef ref) {
    var defData = defSet.getOrNull(ref.target());

    assertNotNull(defData, "Expected def: " + ref.target() + " at " + sourcePos);
    assertEquals(defData.getOrNull(), ref.kind());
  }

  public void assertRef(@NotNull SourcePos sourcePos, @NotNull HighlightInfo.SymRef actualRef, @NotNull Ref expectedRef) {
    checkRef(sourcePos, actualRef);

    var name = expectedRef.name();

    if (name != null) {
      var existName = defMap.getOption(name);
      assertTrue(existName.isDefined(), "Undefined name: " + expectedRef.name());

      var defData = existName.get();
      assertEquals(defData._2.getOrNull(), actualRef.kind());
    }

    assertEquals(actualRef.kind(), expectedRef.kind());
  }

  public static void highlightAndTest(@Language("Aya") @NotNull String code, @Nullable ExpectedHighlightInfo... expected) {
    var sourceFile = new SourceFile("test.aya", Option.none(), code);
    var reporter = ThrowingReporter.INSTANCE;

    var parser = new AyaParserImpl(reporter);
    var stmts = parser.program(sourceFile);
    var resolveInfo = new ResolveInfo(
      new Factory(),
      new EmptyContext(reporter, Path.of(".")).derive("main"),
      stmts
    );

    Stmt.resolve(stmts, resolveInfo, EmptyModuleLoader.INSTANCE);

    var result = SyntaxHighlight.highlight(Option.some(sourceFile), stmts);
    new HighlighterTester(code, result, expected).runTest();
  }

  /// region Helper

  private static @NotNull SourcePos mockPos(int start, int end) {
    return new SourcePos(SourceFile.NONE, start, end, 0, 0, 0, 0);
  }

  public static @NotNull ExpectedHighlightInfo keyword(int begin, int end, @NotNull String display) {
    return new ExpectedHighlightInfo(mockPos(begin, end), new ExpectedHighlightType.Keyword(display));
  }

  public static @NotNull ExpectedHighlightInfo def(int begin, int end, @NotNull String display, @Nullable String name, @Nullable HighlightInfo.DefKind defKind) {
    return new ExpectedHighlightInfo(mockPos(begin, end), new Def(display, name, defKind));
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
    return new ExpectedHighlightInfo(mockPos(begin, end), new Ref(display, name, defKind));
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
  public static @Nullable ExpectedHighlightInfo whatever() {
    return null;
  }

  /// endregion
}
