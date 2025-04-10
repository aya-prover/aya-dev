// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.fixtures;

import org.intellij.lang.annotations.Language;

public interface TailRecError {
  @Language("Aya") String testFib = """
    open inductive Nat | O | S Nat
    tailrec def add (a b : Nat) : Nat elim a, b
    | 0, y => y
    | S x, y => add x (S y)
    
    tailrec def fib (a : Nat) : Nat elim a
    | 0 => 0
    | S 0 => 1
    | S (S x) => add (fib x) (fib (S x))
    """;
}
