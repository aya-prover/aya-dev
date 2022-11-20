// // Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// // Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
// package org.aya.cli;
//
// import org.aya.cli.literate.HighlightInfo;
// import org.aya.util.reporter.Reporter;
// import org.aya.util.reporter.ThrowingReporter;
// import org.intellij.lang.annotations.Language;
// import org.junit.jupiter.api.Test;
//
// import static org.junit.jupiter.api.Assertions.*;
// import static org.aya.cli.HighlighterTester.*;
//
// public class HighlighterTest {
//   public final static Reporter reporter = ThrowingReporter.INSTANCE;
//
//   @Test
//   public void commonTests() {
//     @Language("Aya") String code = """
//       open data Nat
//       | O | S Nat
//
//       def add Nat Nat : Nat
//       | n, 0 => n
//       | S n, m => S (add n m)
//       """;
//
//     highlightAndTest(code,
//       keyword(0, 4, "open"),
//       whatever(),
//       def(10, 13, "Nat", "nat", HighlightInfo.HighlightSymbol.DefKind.Data),
//       def(16, 17, "O", "zero", HighlightInfo.HighlightSymbol.DefKind.Con),
//       def(20, 21, "S", "suc", HighlightInfo.HighlightSymbol.DefKind.Con),
//       ref(22, 25, "Nat", "nat", HighlightInfo.HighlightSymbol.DefKind.Data),
//       whatever(),   // def
//       def(31, 34, "add", HighlightInfo.HighlightSymbol.DefKind.Fn),
//       ref(35, 38, "Nat", "nat", HighlightInfo.HighlightSymbol.DefKind.Data),
//       ref(39, 42, "Nat", "nat", HighlightInfo.HighlightSymbol.DefKind.Data),
//       ref(45, 48, "Nat", "nat", HighlightInfo.HighlightSymbol.DefKind.Data),
//       localDef(51, 52, "n", "n'"),
//       litInt(54, 55, 0),
//       localRef(59, 60, "n", "n'"),
//       ref(63, 64, "S", "suc", HighlightInfo.HighlightSymbol.DefKind.Con),
//       // Note: n' is still available here, so use another name
//       localDef(65, 66, "n", "n''"),
//       localDef(68, 69, "m", "m'"),
//       ref(73, 74, "S", "suc", HighlightInfo.HighlightSymbol.DefKind.Con),
//       ref(76, 79, "add", HighlightInfo.HighlightSymbol.DefKind.Fn),
//       localRef(80, 81, "n", "n''"),
//       localRef(82, 83, "m", "m'")
//     );
//   }
//
//   @Test
//   public void unformatTest() {
//     @Language("Aya") String code = """
//          open
//       data  Nat |
//       O | S Nat
//       """;
//
//     highlightAndTest(code,
//       keyword(3, 7, "open"),
//       keyword(8, 12, "data"),
//       def(14, 17, "Nat", "nat", HighlightInfo.HighlightSymbol.DefKind.Data),
//       def(20, 21, "O", HighlightInfo.HighlightSymbol.DefKind.Con),
//       def(24, 25, "S", HighlightInfo.HighlightSymbol.DefKind.Con),
//       ref(26, 29, "Nat", "nat", HighlightInfo.HighlightSymbol.DefKind.Data));
//   }
//
//   @Test
//   public void incorrectTest() {
//     assertThrows(Throwable.class, () -> {
//       @Language("Aya") String code = """
//         open data List (A : Type)
//         | nil
//         | cons A (List A)
//         """;
//
//       highlightAndTest(code,
//         whatever(),
//         whatever(),
//         def(10, 14, "L1st", "list", HighlightInfo.HighlightSymbol.DefKind.Data));
//     });
//   }
// }
