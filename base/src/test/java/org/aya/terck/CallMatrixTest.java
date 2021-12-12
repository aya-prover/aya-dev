// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CallMatrixTest {
  CallMatrix<String, String> A = new CallMatrix<>(null,
    "f", "g",
    ImmutableSeq.of("f1"),
    ImmutableSeq.of("g1", "g2"));
  CallMatrix<String, String> B = new CallMatrix<>(null,
    "g", "h",
    ImmutableSeq.of("g1", "g2"),
    ImmutableSeq.of("h1", "h2", "h3"));

  @Test
  public void invalidCombine() {
    assertThrows(IllegalArgumentException.class, () -> CallMatrix.combine(B, A));
  }

  @Test
  public void combine() {
    // f calls g with call matrix A, g calls h with call matrix B => f indirectly calls h with BA
    var BA = CallMatrix.combine(A, B);
    assertEquals(BA.rows(), B.rows());
    assertEquals(BA.cols(), A.cols());
    assertEquals(BA.domain(), A.domain());
    assertEquals(BA.codomain(), B.codomain());

    assertEquals("f", BA.domain());
    assertEquals("h", BA.codomain());

    assertEquals(BA.rows(), 3);
    assertEquals(BA.cols(), 1);
  }
}
