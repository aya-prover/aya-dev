// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.experiments;

import org.aya.core.def.FnDef;
import org.aya.generic.util.NormalizeMode;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.tyck.TyckDeclTest;
import org.aya.tyck.tycker.TyckState;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class NormalizeHugeChurch {
  public static void println(@NotNull String s) {
    // System.out.println(s);
  }

  @Test @Timeout(value = 5000) public void ppBench() {
    var startup = System.currentTimeMillis();
    var res = TyckDeclTest.successTyckDecls("""
      def Num => Fn (x : Type 0) -> (x -> x) -> (x -> x)
      def zero : Num => \\ A f x => x
      def suc (a : Num) : Num => \\ A f x => a A f (f x)
      def add (a b : Num) : Num => \\A f x => a A f (b A f x)
      def mul (a b : Num) : Num => \\A f x => a A (b A f) x
      def #2 : Num => suc (suc zero)
      def #4 : Num => mul #2 #2
      def #16 : Num => mul #4 #4
      def #256 : Num => add #16 #16
      """);
    var state = new TyckState(res.component1());
    var decls = res.component2();
    var last = ((FnDef) decls.last()).body.getLeftValue();
    println("Tyck: " + (System.currentTimeMillis() - startup));
    startup = System.currentTimeMillis();
    var nf = last.normalize(state, NormalizeMode.NF);
    println("Normalize: " + (System.currentTimeMillis() - startup));
    startup = System.currentTimeMillis();
    var doc = nf.toDoc(AyaPrettierOptions.informative());
    println("Docify: " + (System.currentTimeMillis() - startup));
    startup = System.currentTimeMillis();
    var text = doc.debugRender();
    println("Stringify: " + (System.currentTimeMillis() - startup));
    println(text);
  }
}
