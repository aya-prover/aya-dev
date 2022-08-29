// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cubical;

import org.aya.tyck.TyckDeclTest;
import org.junit.jupiter.api.Test;

public class PathTest {
  @Test public void refl() {
    TyckDeclTest.successTyckDecls("""
      def infix = {A : Type} (a b : A) : Type =>
        [| i |] A {| i 0 := a | i 1 := b |}
          
      def idp {A : Type} {a : A} : a = a =>
        \\i => a
      """);
  }

  @Test public void cong() {
    TyckDeclTest.successTyckDecls("""
      def infix = {A : Type} (a b : A) : Type =>
      [| i |] A {| i 0 := a | i 1 := b |}
          
      def idp {A : Type} {a : A} : a = a =>
        \\i => a
      
      def cong
        {A B : Type}
        (f : A -> B)
        (a b : A)
        (p : a = b)
        : --------------
        f a = f b
        => \\i => f (p i)
      """);
  }

  @Test public void funExt() {
    TyckDeclTest.successTyckDecls("""
      
      def infix = {A : Type} (a b : A) : Type =>
      [| i |] A {| i 0 := a | i 1 := b |}
          
      def idp {A : Type} {a : A} : a = a =>
        \\i => a
      
      def funExt
        {A B : Type}
        (f g : A -> B)
        (p : Pi (a : A) -> f a = g a)
        : ---------------------------------
        f = g
        => \\i a => p a i
      """);
  }
}
