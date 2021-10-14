// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import kala.collection.immutable.ImmutableMap;
import org.aya.api.ref.LocalVar;
import org.aya.value.Value;

public record Environment(ImmutableMap<LocalVar, Value> map) {
  public Value lookup(LocalVar var) {
    return map.get(var);
  }

  public Environment added(LocalVar var, Value val) {
    return new Environment(map.updated(var, val));
  }
}
