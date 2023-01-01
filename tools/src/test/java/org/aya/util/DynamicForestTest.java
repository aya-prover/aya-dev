// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.immutable.ImmutableArray;
import kala.collection.mutable.MutableArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DynamicForestTest {
  @Test public void trivial() {
    var a = DynamicForest.create();
    var b = DynamicForest.create();
    var c = DynamicForest.create();
    var d = DynamicForest.create();
    var e = DynamicForest.create();

    for (var i : ImmutableArray.of(a, b, c, d, e))
      for (var j : ImmutableArray.of(a, b, c, d, e))
        if (i != j) assertFalse(i.isConnected(j));

    a.connect(b);
    c.connect(d);

    assertTrue(a.isConnected(b));
    assertTrue(b.isConnected(a));
    assertTrue(a.isDirectlyConnected(b));
    assertTrue(b.isDirectlyConnected(a));
    assertTrue(c.isConnected(d));
    assertTrue(d.isConnected(c));
    assertTrue(c.isDirectlyConnected(d));
    assertTrue(d.isDirectlyConnected(c));

    for (var i : ImmutableArray.of(a, b))
      for (var j : ImmutableArray.of(c, d, e))
        assertFalse(i.isConnected(j));

    for (var i : ImmutableArray.of(c, d))
      for (var j : ImmutableArray.of(a, b, e))
        assertFalse(i.isConnected(j));

    for (var i : ImmutableArray.of(a, b, c, d))
      assertFalse(e.isConnected(i));

    e.connect(b);
    a.connect(d);

    for (var i : ImmutableArray.of(a, b, c, d, e))
      for (var j : ImmutableArray.of(a, b, c, d, e))
        assertTrue(i.isConnected(j));

    assertFalse(e.isDirectlyConnected(c));
    assertFalse(d.isDirectlyConnected(b));

    d.disconnect(c);
    assertFalse(d.isDirectlyConnected(c));
    assertFalse(c.isConnected(d));

    for (var i : ImmutableArray.of(a, b, d, e))
      for (var j : ImmutableArray.of(a, b, d, e))
        assertTrue(i.isConnected(j));
  }


  @Test public void random() {
    MutableArrayList<DynamicForest.Handle> handles = MutableArrayList.create();

    for (int j = 0; j < 100; ++j)
      handles.append(DynamicForest.create());

    for (int j = 1; j < 100; ++j)
      handles.get(j).connect(handles.get(j - 1));

    for (int j = 0; j < 100; ++j)
      for (int k = 0; k < 100; ++k)
        assertTrue(handles.get(j).isConnected(handles.get(k)));

    for (int j = 10; j < 100; j += 10)
      handles.get(j - 1).disconnect(handles.get(j));

    for (int j = 0; j < 100; j += 10)
      for (int k = j; k < j + 10; ++k)
        for (int g = j; g < j + 10; ++g)
          assertTrue(handles.get(k).isConnected(handles.get(g)));

    for (int j = 0; j < 100; j += 10) {
      for (int k = j; k < j + 10; ++k) {
        for (int g = 0; g < j; ++g)
          assertFalse(handles.get(k).isConnected(handles.get(g)));
        for (int g = j + 10; g < 100; ++g)
          assertFalse(handles.get(k).isConnected(handles.get(g)));
      }
    }

    var count = 0;
    for (int j = 0; j < 9; ++j) {
      handles.get(count + j).connect(handles.get(count + 10 + j));
      count += 10;
    }

    for (int j = 0; j < 100; ++j)
      for (int k = 0; k < 100; ++k)
        assertTrue(handles.get(j).isConnected(handles.get(k)));

    for (int j = 0; j < 40; j += 10) {
      handles.get(j + 4).disconnect(handles.get(j + 5));
      for (int k = j + 5; k < j + 10; ++k)
        for (int g = j + 5; g < j + 10; ++g)
          assertTrue(handles.get(k).isConnected(handles.get(g)));
      for (int k = j + 5; k < j + 10; ++k)
        for (int g = 0; g < j + 5; ++g)
          assertFalse(handles.get(k).isConnected(handles.get(g)));
      for (int k = j + 5; k < j + 10; ++k)
        for (int g = j + 10; g < 100; ++g)
          assertFalse(handles.get(k).isConnected(handles.get(g)));
      handles.get(j + 4).connect(handles.get(j + 5));
    }

    for (int j = 0; j < 100; ++j)
      for (int k = 0; k < 100; ++k)
        assertTrue(handles.get(j).isConnected(handles.get(k)));

  }

}
