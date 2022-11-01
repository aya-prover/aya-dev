// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repr;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.GenericDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.repr.ShapeMatcher;
import org.aya.tyck.TyckDeclTest;
import org.aya.util.distill.DistillerOptions;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("UnknownLanguage")
public class ShapeMatcherTest {
  @Test
  public void matchNat() {
    match(true, AyaShape.NAT_SHAPE, "open data Nat | zero | suc Nat");
    match(true, AyaShape.NAT_SHAPE, "open data Nat | suc Nat | zero");
    match(true, AyaShape.NAT_SHAPE, "open data Nat | z | s Nat");

    match(ImmutableSeq.of(true, false), AyaShape.NAT_SHAPE, """
      open data Nat | zero | suc Nat
      open data Fin (n : Nat) | suc n => fzero | suc n => fsuc (Fin n)
      """);

    match(false, AyaShape.NAT_SHAPE, "open data Nat | s | z");
  }

  @Test
  public void matchList() {
    match(true, AyaShape.LIST_SHAPE, "data List (A : Prop) | nil | cons A (List A)");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons A (List A)");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | cons A (List A) | nil");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | infixr :< A (List A)");

    match(false, AyaShape.LIST_SHAPE, "data List | nil | cons");
    match(false, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons");
    match(false, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons A A");
    match(false, AyaShape.LIST_SHAPE, "data List (A : Type) | nil A | cons A (List A)");
    match(false, AyaShape.LIST_SHAPE, "data List (A B : Type) | nil | cons A A");
    match(false, AyaShape.LIST_SHAPE, "data List (A B : Type) | nil | cons A B");
    match(ImmutableSeq.of(false, false), AyaShape.LIST_SHAPE, """
      data False
      data List (A : Type)
        | nil
        | cons A (List False)
      """);
  }

  @Test
  public void capture() {
    var match = match(true, AyaShape.NAT_SHAPE, "open data Nat | zero | suc (pred : Nat)");
    assertNotNull(match);
    assertEquals("| zero", match.captures().get("zero").core.toDoc(DistillerOptions.pretty()).debugRender());
    assertEquals("| suc (pred : Nat)", match.captures().get("suc").core.toDoc(DistillerOptions.pretty()).debugRender());
    match = match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | infixr :< (a : A) (tail : List A)");
    assertNotNull(match);
    assertEquals("| nil", match.captures().get("nil").core.toDoc(DistillerOptions.pretty()).debugRender());
    assertEquals("| :< (a : A) (tail : List A)", match.captures().get("cons").core.toDoc(DistillerOptions.pretty()).debugRender());
  }

  @Test
  public void matchWeirdList() {
    match(true, AyaShape.LIST_SHAPE, "data List {A : Type} | nil | cons A (List {A})");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons {A} (List A)");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons A {List A}");
    match(true, AyaShape.LIST_SHAPE, "data List {A : Type} | nil | cons {A} (List {A})");
    match(true, AyaShape.LIST_SHAPE, "data List {A : Type} | nil | cons A {List {A}}");
    match(true, AyaShape.LIST_SHAPE, "data List (A : Type) | nil | cons {A} {List A}");
    match(true, AyaShape.LIST_SHAPE, "data List {A : Type} | nil | cons {A} {List {A}}");
  }

  public @Nullable ShapeMatcher.Result match(boolean should, @NotNull AyaShape shape, @Language("Aya") @NonNls @NotNull String code) {
    var def = TyckDeclTest.successTyckDecls(code)._2;
    return check(ImmutableSeq.fill(def.size(), should), shape, def).firstOrNull();
  }

  public void match(@NotNull ImmutableSeq<Boolean> should, @NotNull AyaShape shape, @Language("Aya") @NonNls @NotNull String code) {
    var def = TyckDeclTest.successTyckDecls(code)._2;
    check(should, shape, def);
  }

  private static ImmutableSeq<ShapeMatcher.Result> check(@NotNull ImmutableSeq<Boolean> should, @NotNull AyaShape shape, @NotNull ImmutableSeq<GenericDef> def) {
    return def.zipView(should).flatMap(tup -> {
      var match = ShapeMatcher.match(shape, tup._1);
      assertEquals(tup._2, match.isDefined());
      return match;
    }).toImmutableSeq();
  }
}
