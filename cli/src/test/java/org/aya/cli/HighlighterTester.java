// // Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// // Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
// package org.aya.cli;
//
// import com.intellij.openapi.util.TextRange;
// import kala.collection.Seq;
// import kala.collection.mutable.MutableMap;
// import kala.control.Option;
// import kala.tuple.Tuple;
// import kala.tuple.Tuple2;
// import org.aya.cli.HighlighterTester.ExpectedHighlightType.Def;
// import org.aya.cli.HighlighterTester.ExpectedHighlightType.LitInt;
// import org.aya.cli.HighlighterTester.ExpectedHighlightType.Ref;
// import org.aya.cli.literate.HighlightInfo;
// import org.aya.cli.literate.HighlighterUtil;
// import org.aya.cli.parse.AyaParserImpl;
// import org.aya.concrete.desugar.AyaBinOpSet;
// import org.aya.concrete.stmt.Stmt;
// import org.aya.core.def.PrimDef.Factory;
// import org.aya.parser.AyaParserDefinitionBase;
// import org.aya.resolve.ResolveInfo;
// import org.aya.resolve.context.EmptyContext;
// import org.aya.resolve.module.EmptyModuleLoader;
// import org.aya.util.distill.DistillerOptions;
// import org.aya.util.error.SourceFile;
// import org.aya.util.reporter.DelayedReporter;
// import org.aya.util.reporter.ThrowingReporter;
// import org.intellij.lang.annotations.Language;
// import org.jetbrains.annotations.Contract;
// import org.jetbrains.annotations.NotNull;
// import org.jetbrains.annotations.Nullable;
//
// import java.nio.file.Path;
// import java.util.PriorityQueue;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// public class HighlighterTester {
//   record ExpectedHighlightInfo(@NotNull TextRange range, @NotNull ExpectedHighlightType expected) {}
//
//   public sealed interface ExpectedHighlightType {
//     @NotNull String display();
//
//     record Def(@Override @NotNull String display, @Nullable String name, @Nullable DefKind kind)
//       implements ExpectedHighlightType {
//     }
//
//     record Ref(@Override @NotNull String display, @Nullable String name,
//                @Nullable DefKind kind) implements ExpectedHighlightType {}
//
//     record Keyword(@Override @NotNull String display) implements ExpectedHighlightType {}
//
//     record LitString(@NotNull String expected) implements ExpectedHighlightType {
//       @Override
//       public @NotNull String display() {
//         return '\"' + expected + '\"';
//       }
//     }
//
//     record LitInt(int expected) implements ExpectedHighlightType {
//       @Override
//       public @NotNull String display() {
//         return Integer.toString(expected);
//       }
//     }
//   }
//
//   public final @NotNull String sourceCode;
//   public final @NotNull PriorityQueue<HighlightInfo> actual;
//
//   // NotNull array with Nullable element
//   public final ExpectedHighlightInfo[] expected;
//
//   // TODO[hoshino]: Inductive Defined (allow scope)
//   // (User-Defined Name, (Unique ID, Nullable Def Kind))
//   public final MutableMap<String, Tuple2<String, Option<DefKind>>> defMap = MutableMap.create();
//   public final MutableMap<String, Option<DefKind>> defSet = MutableMap.create();
//
//   public HighlighterTester(@NotNull String sourceCode, @NotNull PriorityQueue<HighlightInfo> actual, @Nullable ExpectedHighlightInfo[] expected) {
//     this.sourceCode = sourceCode;
//     this.actual = actual;
//     this.expected = expected;
//   }
//
//   public void runTest() {
//     runTest(Seq.generateUntilNull(actual::poll), Seq.of(expected));
//   }
//
//   public void runTest(@NotNull Seq<HighlightInfo> actuals, @NotNull Seq<ExpectedHighlightInfo> expecteds) {
//     assertEquals(actuals.size(), expecteds.size(), "size mismatch");
//     for (var tup : actuals.zipView(expecteds)) {
//       var actual = tup._1;
//       var expected = tup._2;
//
//       if (expected == null) {
//         switch (actual.type()) {
//           case SymDef def -> checkDef(actual.sourcePos(), def);
//           case SymRef ref -> checkRef(actual.sourcePos(), ref);
//           default -> {}
//         }
//
//         continue;
//       }
//
//       assertEquals(expected.range(), actual.sourcePos());
//
//       var sourcePos = actual.sourcePos();
//       var expectedText = expected.expected.display();
//       var actualText = sourcePos.substring(sourceCode);
//
//       assertEquals(expectedText, actualText, "at " + sourcePos);
//
//       switch (actual.type()) {
//         case Lit(var ty)
//           when ty == LitKind.Keyword && expected.expected() instanceof ExpectedHighlightType.Keyword -> {
//         }
//         case Lit(var ty)
//           when ty == LitKind.Int && expected.expected() instanceof LitInt -> {
//         }
//         case Lit(var ty)
//           when ty == LitKind.String && expected.expected() instanceof ExpectedHighlightType.LitString -> {
//         }
//
//         case SymDef def
//           when expected.expected() instanceof Def expectedDef -> assertDef(sourcePos, def, expectedDef);
//
//         case SymRef ref
//           when expected.expected() instanceof Ref expectedRef -> assertRef(sourcePos, ref, expectedRef);
//
//         case SymError error -> throw new UnsupportedOperationException("TODO");   // TODO
//
//         default ->
//           fail("expected: " + expected.getClass().getSimpleName() + ", but actual: " + actual.getClass().getSimpleName());
//       }
//     }
//   }
//
//   /**
//    * Check no duplicated def.
//    */
//   public void checkDef(@NotNull TextRange sourcePos, @NotNull SymDef def) {
//     var existDef = defSet.containsKey(def.target());
//     assertFalse(existDef, "Duplicated def: " + def.target() + " at " + sourcePos);
//
//     defSet.put(def.target(), Option.ofNullable(def.kind()));
//   }
//
//   public void assertDef(@NotNull TextRange sourcePos, @NotNull SymDef actualDef, @NotNull Def expectedDef) {
//     checkDef(sourcePos, actualDef);
//
//     assertEquals(expectedDef.kind(), actualDef.kind());
//
//     var name = expectedDef.name();
//
//     if (name != null) {
//       var existName = defMap.getOption(name);
//       assertFalse(existName.isDefined(), "Duplicated name: " + expectedDef.name());
//
//       defMap.put(name, Tuple.of(actualDef.target(), Option.ofNullable(actualDef.kind())));
//     }
//   }
//
//   /**
//    * Check the reference
//    */
//   public void checkRef(@NotNull TextRange sourcePos, @NotNull SymRef ref) {
//     var defData = defSet.getOrNull(ref.target());
//
//     assertNotNull(defData, "Expected def: " + ref.target() + " at " + sourcePos);
//     assertEquals(defData.getOrNull(), ref.kind());
//   }
//
//   public void assertRef(@NotNull TextRange sourcePos, @NotNull SymRef actualRef, @NotNull Ref expectedRef) {
//     checkRef(sourcePos, actualRef);
//
//     var name = expectedRef.name();
//
//     if (name != null) {
//       var existName = defMap.getOption(name);
//       assertTrue(existName.isDefined(), "Undefined name: " + expectedRef.name());
//
//       var defData = existName.get();
//       assertEquals(defData._2.getOrNull(), actualRef.kind());
//     }
//
//     assertEquals(actualRef.kind(), expectedRef.kind());
//   }
//
//   public static void highlightAndTest(@Language("Aya") @NotNull String code, @Nullable ExpectedHighlightInfo... expected) {
//     var fileName = "null";
//     var reporter = newReporter();
//
//     var lexer = AyaParserDefinitionBase.createLexer(false);
//     lexer.reset(code, 0, code.length(), 0);
//     var tokens = lexer.allTheWayDown();
//
//     var parser = new AyaParserImpl(reporter);
//     var stmts = parser.program(new SourceFile(fileName, Option.none(), code));
//     var resolveInfo = new ResolveInfo(
//       new Factory(),
//       new EmptyContext(reporter, Path.of(".")).derive(fileName),
//       stmts, new AyaBinOpSet(reporter)
//     );
//
//     Stmt.resolveWithoutDesugar(stmts, resolveInfo, EmptyModuleLoader.INSTANCE);
//
//     var anyError = reporter.anyError();
//     reporter.close();
//
//     if (anyError) {
//       fail("expected: no error, but actual: error");
//     }
//
//     var result = HighlighterUtil.highlight(stmts, DistillerOptions.debug());
//     doTest(code, HighlighterUtil.highlightKeywords(result, tokens), expected);
//   }
//
//   public static void doTest(@NotNull String sourceCode, @NotNull PriorityQueue<HighlightInfo> actual, @Nullable ExpectedHighlightInfo... expected) {
//     new HighlighterTester(sourceCode, actual, expected).runTest();
//   }
//
//   public static @NotNull DelayedReporter newReporter() {
//     return new DelayedReporter(problem ->
//       System.err.println(ThrowingReporter.errorMessage(
//         problem, DistillerOptions.informative(), false, false, 80))
//     );
//   }
//
//   /// region Helper
//
//   public static @NotNull ExpectedHighlightInfo keyword(int begin, int end, @NotNull String display) {
//     return new ExpectedHighlightInfo(new TextRange(begin, end), new ExpectedHighlightType.Keyword(display));
//   }
//
//   public static @NotNull ExpectedHighlightInfo def(int begin, int end, @NotNull String display, @Nullable String name, @Nullable DefKind defKind) {
//     return new ExpectedHighlightInfo(new TextRange(begin, end), new Def(display, name, defKind));
//   }
//
//   public static @NotNull ExpectedHighlightInfo def(int begin, int end, @NotNull String display, @Nullable DefKind defKind) {
//     return def(begin, end, display, null, defKind);
//   }
//
//   public static @NotNull ExpectedHighlightInfo localDef(int begin, int end, @NotNull String display, @Nullable String name) {
//     return def(begin, end, display, name, DefKind.Local);
//   }
//
//   public static @NotNull ExpectedHighlightInfo localDef(int begin, int end, @NotNull String display) {
//     return localDef(begin, end, display, null);
//   }
//
//   public static @NotNull ExpectedHighlightInfo ref(int begin, int end, @NotNull String display, @Nullable String name, DefKind defKind) {
//     return new ExpectedHighlightInfo(new TextRange(begin, end), new Ref(display, name, defKind));
//   }
//
//   public static @NotNull ExpectedHighlightInfo ref(int begin, int end, @NotNull String display, DefKind defKind) {
//     return ref(begin, end, display, null, defKind);
//   }
//
//   public static @NotNull ExpectedHighlightInfo localRef(int begin, int end, @NotNull String display, @Nullable String name) {
//     return ref(begin, end, display, name, DefKind.Local);
//   }
//
//   public static @NotNull ExpectedHighlightInfo localRef(int begin, int end, @NotNull String display) {
//     return localRef(begin, end, display, null);
//   }
//
//   public static @NotNull ExpectedHighlightInfo litInt(int begin, int end, int display) {
//     return new ExpectedHighlightInfo(new TextRange(begin, end), new LitInt(display));
//   }
//
//   @Contract(" -> null")
//   public static @Nullable ExpectedHighlightInfo whatever() {
//     return null;
//   }
//
//   /// endregion
// }
