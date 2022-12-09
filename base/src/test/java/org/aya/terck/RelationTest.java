// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.FnCall;
import org.aya.ref.DefVar;
import org.aya.util.terck.CallMatrix;
import org.aya.util.terck.Relation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RelationTest {
  @Test public void ring() {
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

  @Test public void compensate() {
    assertEquals(Relation.decr(true, 1), Relation.decr(true, -2).mul(Relation.decr(true, 3)));
    assertEquals(Relation.decr(true, 1), Relation.decr(false, 3).mul(Relation.decr(true, -2)));
    assertEquals(Relation.decr(false, 1), Relation.decr(false, 3).mul(Relation.decr(false, -2)));
  }

  @Test public void pretty() {
    var dummy = new FnCall(DefVar.empty("f"), 0, ImmutableSeq.empty());
    // only used for error reporting, so it's fine to mock it.
    var mat = new CallMatrix<>(dummy, "f", "g",
      ImmutableSeq.of("a", "b", "c"),
      ImmutableSeq.of("a", "b", "c"));
    mat.set("a", "a", Relation.eq());
    mat.set("a", "b", Relation.lt());
    mat.set("a", "c", Relation.unk());
    mat.set("b", "b", Relation.decr(true, 2));
    mat.set("c", "c", Relation.decr(false, 1));
    assertEquals(
      """
          =   ?   ?
         -1  -2   ?
          ?   ? !-1
        """.stripTrailing(), mat.toDoc().debugRender());
  }
}
