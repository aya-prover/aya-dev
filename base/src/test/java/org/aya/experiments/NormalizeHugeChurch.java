// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.experiments;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.NormalizeMode;
import org.aya.core.def.FnDef;
import org.aya.tyck.TyckDeclTest;

public class NormalizeHugeChurch {
  public static void main(String[] args) {
    var startup = System.currentTimeMillis();
    var decls = TyckDeclTest.successTyckDecls("""
      def Num => Pi (x : Type 0) -> (x -> x) -> (x -> x)
      def zero : Num => \\ A f x => x
      def suc (a : Num) : Num => \\ A f x => a A f (f x)
      def add (a b : Num) : Num => \\A f x => a A f (b A f x)
      def mul (a b : Num) : Num => \\A f x => a A (b A f) x
      def #2 : Num => suc (suc zero)
      def #4 : Num => mul #2 #2
      def #16 : Num => mul #4 #4
      def #256 : Num => mul #16 #16
      def #1024 : Num => mul #4 #256
      """);
    var last = ((FnDef) decls.last()).body.getLeftValue();
    System.out.println("Tyck: " + (System.currentTimeMillis() - startup));
    startup = System.currentTimeMillis();
    var nf = last.normalize(NormalizeMode.NF);
    System.out.println("Normalize: " + (System.currentTimeMillis() - startup));
    startup = System.currentTimeMillis();
    var doc = nf.toDoc(DistillerOptions.DEFAULT);
    System.out.println("Docify: " + (System.currentTimeMillis() - startup));
    startup = System.currentTimeMillis();
    var text = doc.debugRender();
    System.out.println("Stringify: " + (System.currentTimeMillis() - startup));
    System.out.println(text);
  }
}
