// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cubical;

import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckDeclTest;
import org.junit.jupiter.api.Test;

import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PathTest {
  @Test public void refl() {
    var res = TyckDeclTest.successTyckDecls("""
      prim I
      prim intervalInv
      inline def ~ => intervalInv
      def infix = {A : Type} (a b : A) : Type =>
        [| i |] A { ~ i := a | i := b }
          
      def idp {A : Type} {a : A} : a = a =>
        \\i => a
      """);
    IntFunction<Doc> prettier = i -> res.component2().get(i).toDoc(AyaPrettierOptions.debug());
    assertEquals("def = {A : Type 0} (a b : A) : Type 0 => a = b",
      prettier.apply(3).debugRender());
    assertEquals("def idp {A : Type 0} {a : A} : (=) {A} a a => \\ i => a",
      prettier.apply(4).debugRender());
  }

  @Test public void cong() {
    TyckDeclTest.successTyckDecls("""
      prim I
      prim intervalInv
      def ~ => intervalInv
            
      def infix = {A : Type} (a b : A) : Type =>
      [| i |] A { ~ i := a | i := b }
          
      def idp {A : Type} {a : A} : a = a =>
        \\i => a
            
      def cong
        {A B : Type}
        (f : A -> B)
        (a b : A)
        (p : a = b)
        : f a = f b
        => \\i => f (p i)
      """);
  }

  @Test public void funExt() {
    TyckDeclTest.successTyckDecls("""
      prim I
      prim intervalInv
            
      def ~ => intervalInv
      def infix = {A : Type} (a b : A) : Type =>
      [| i |] A { ~ i := a | i := b }
          
      def idp {A : Type} {a : A} : a = a =>
        \\i => a
            
      def funExt
        {A B : Type}
        (f g : A -> B)
        (p : Fn (a : A) -> f a = g a)
        : f = g
        => \\i a => p a i
      """);
  }

  @Test public void partialConv() {
    TyckDeclTest.successTyckDecls("""
      prim I
      prim Partial
      prim intervalInv
            
      inline def ~ => intervalInv
            
      def infix = {A : Type} (a b : A) : Type =>
        [| i |] A { ~ i := a | i := b }
          
      def idp {A : Type} {a : A} : a = a =>
        \\i => a
        
      def p1 (A : Type) (a : A) (i : I) : Partial (~ i) A =>
        {| ~ i := a |}
      def p2 (A : Type) (b : A) (j : I) : Partial (~ j) A =>
        {| ~ j := b |}
      def p1=p2 (A : Type) (a : A) (i : I) : p1 A a i = p2 A a i =>
        idp
          
      def cmp {A : Type} (x : A)
        : [| i j |] (Partial (~ j) A) { ~ i := p1 A x j }
        => \\i j => p2 A x j
      """);
  }
}
