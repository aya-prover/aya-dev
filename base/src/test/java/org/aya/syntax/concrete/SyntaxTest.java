// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete;

import org.aya.syntax.SyntaxTestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SyntaxTest {
  @Test public void test0() {
    var res = SyntaxTestUtil.parse("""
      def foo (f : Type -> Type 0) (a : Type 0) : Type 0 => f a
      def bar (A : Type 0) : A -> A => fn x => x
      """);
    assertTrue(res.isNotEmpty());
  }
}
