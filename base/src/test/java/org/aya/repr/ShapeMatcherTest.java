// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repr;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.repr.AyaShape;
import org.aya.core.repr.CodeShape;
import org.aya.core.repr.ShapeMatcher;
import org.aya.tyck.TyckDeclTest;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("UnknownLanguage")
public class ShapeMatcherTest {
  @Test
  public void matchNat() {
    match(true, AyaShape.AyaIntLitShape.DATA_NAT, "open data Nat | zero | suc Nat");
    match(true, AyaShape.AyaIntLitShape.DATA_NAT, "open data Nat | suc Nat | zero");
    match(true, AyaShape.AyaIntLitShape.DATA_NAT, "open data Nat | z | s Nat");

    match(ImmutableSeq.of(true, false), AyaShape.AyaIntLitShape.DATA_NAT, """
    open data Nat | zero | suc Nat
    open data Fin (n : Nat) | suc n => fzero | suc n => fsuc (Fin n)
    """);

    match(false, AyaShape.AyaIntLitShape.DATA_NAT, "open data Nat | s | z");
  }

  @Test
  public void matchList() {
    match(true, AyaShape.AyaListShape.DATA_LIST, "data List (A : Prop) | nil | cons A (List A)");
    match(true, AyaShape.AyaListShape.DATA_LIST, "data List (A : Type) | nil | cons A (List A)");
    match(true, AyaShape.AyaListShape.DATA_LIST, "data List (A : Type) | cons A (List A) | nil");
    match(true, AyaShape.AyaListShape.DATA_LIST, "data List (A : Type) | nil | infixr :< A (List A)");

    match(false, AyaShape.AyaListShape.DATA_LIST, "data List | nil | cons");
    match(false, AyaShape.AyaListShape.DATA_LIST, "data List (A : Type) | nil | cons");
    match(false, AyaShape.AyaListShape.DATA_LIST, "data List (A : Type) | nil | cons A A");
    match(false, AyaShape.AyaListShape.DATA_LIST, "data List (A B : Type) | nil | cons A A");
    match(false, AyaShape.AyaListShape.DATA_LIST, "data List (A B : Type) | nil | cons A B");
    match(ImmutableSeq.of(false, false), AyaShape.AyaListShape.DATA_LIST, """
      data False
      
      data List (A : Type)
        | nil
        | cons A (List False)
      """);
  }

  public void match(boolean should, @NotNull CodeShape shape, @Language("Aya") @NonNls @NotNull String code) {
    var def = TyckDeclTest.successTyckDecls(code)._2;
    def.forEach(d -> assertEquals(should, ShapeMatcher.match(shape, d)));
  }

  public void match(@NotNull ImmutableSeq<Boolean> should, @NotNull CodeShape shape, @Language("Aya") @NonNls @NotNull String code) {
    var def = TyckDeclTest.successTyckDecls(code)._2;
    def.zipView(should).forEach(tup -> assertEquals(tup._2, ShapeMatcher.match(shape, tup._1)));
  }
}
