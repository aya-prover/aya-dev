// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RelationTest {
  @Test
  public void ring() {
    assertEquals(Relation.unk(), Relation.unk().mul(Relation.lt()));
    assertEquals(Relation.unk(), Relation.unk().mul(Relation.eq()));
    assertEquals(Relation.unk(), Relation.eq().mul(Relation.unk()));
    assertEquals(Relation.lt(), Relation.eq().mul(Relation.lt()));
    assertEquals(Relation.eq(), Relation.eq().mul(Relation.eq()));

    assertEquals(Relation.lt(), Relation.lt().add(Relation.eq()));
    assertEquals(Relation.eq(), Relation.eq().add(Relation.eq()));
    assertEquals(Relation.eq(), Relation.unk().add(Relation.eq()));
    assertEquals(Relation.lt(), Relation.unk().add(Relation.lt()));
  }
}
