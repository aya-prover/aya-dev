// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RelationTest {
  @Test
  public void ring() {
    assertEquals(Relation.Unknown, Relation.Unknown.mul(Relation.LessThan));
    assertEquals(Relation.Unknown, Relation.Unknown.mul(Relation.Equal));
    assertEquals(Relation.Unknown, Relation.Equal.mul(Relation.Unknown));
    assertEquals(Relation.LessThan, Relation.Equal.mul(Relation.LessThan));
    assertEquals(Relation.Equal, Relation.Equal.mul(Relation.Equal));

    assertEquals(Relation.LessThan, Relation.LessThan.add(Relation.Equal));
    assertEquals(Relation.Equal, Relation.Equal.add(Relation.Equal));
    assertEquals(Relation.Equal, Relation.Unknown.add(Relation.Equal));
    assertEquals(Relation.LessThan, Relation.Unknown.add(Relation.LessThan));
  }
}
