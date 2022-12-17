// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public class PrettierTest {
  @Test
  public void listPattern() {
    parseAndPretty("""
      def foo (l : List Nat) : List Nat
        | [ ] => nil
        | [ 1 ] => 1 :< nil
        | [ 1, e2 ] => 1 :< e2 :< nil
        | _ => l
      """, """
      def foo (l : List Nat) : List Nat
        | [  ] => nil
        | [ 1 ] => 1 :< nil
        | [ 1, e2 ] => 1 :< e2 :< nil
        | _ => l
      """);
  }

  @Test
  public void letExprAndSaveSomeCoverage() {
    parseAndPretty("""
      def foo =>
        let | a := A | b := B in c
      """, """
      def foo => let
      | a := A
      | b := B
      in c
      """);

    parseAndPretty("""
      def foo =>
        let a := A in b
      """, """
      def foo => let a := A in b
      """);

    parseAndPretty("""
      def foo =>
        let a := A in
        let b := B in
        c
      """, """
      def foo => let
      | a := A
      | b := B
      in c
      """);
  }

  @Test
  public void as() {
    parseAndPretty("""
      open data ImNat | O | S {n : ImNat}
            
      def matchIt {x : ImNat} (y : ImNat) : ImNat
      | {O} as x, y => y
      | {O as x}, S {_} as y => y
      | {S x' as x}, S {y' as y} => S y
      """, """
      data ImNat
        | O
        | S {n : ImNat}
      open ImNat hiding ()
      def matchIt {x : ImNat} (y : ImNat) : ImNat
        | {O} as x, y => y
        | {O} as x, S {_} as y => y
        | {S x'} as x, S {y' as y} => S y
      """);
  }

  // we test pretty instead of parsing
  public static void parseAndPretty(@NotNull @NonNls @Language("Aya") String code, @NotNull @NonNls @Language("Aya") String pretty) {
    ParseTest.parseAndPretty(code, pretty);
  }
}
