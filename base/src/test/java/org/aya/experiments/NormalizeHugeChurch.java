// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.experiments;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.NormalizeMode;
import org.aya.core.def.FnDef;
import org.aya.tyck.TyckDeclTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class NormalizeHugeChurch {
  public static void println(@NotNull String s) {
    // System.out.println(s);
  }

  @Test @Timeout(value = 5000) public void ppBench() {
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
      def #256 : Num => add #16 #16
      """);
    var last = ((FnDef) decls.last()).body.getLeftValue();
    println("Tyck: " + (System.currentTimeMillis() - startup));
    startup = System.currentTimeMillis();
    var nf = last.normalize(null, NormalizeMode.NF);
    println("Normalize: " + (System.currentTimeMillis() - startup));
    startup = System.currentTimeMillis();
    var doc = nf.toDoc(DistillerOptions.DEFAULT);
    println("Docify: " + (System.currentTimeMillis() - startup));
    startup = System.currentTimeMillis();
    var text = doc.debugRender();
    println("Stringify: " + (System.currentTimeMillis() - startup));
    println(text);
  }
}
