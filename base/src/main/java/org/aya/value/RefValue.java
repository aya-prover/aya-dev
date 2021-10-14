// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.LocalVar;
import org.aya.core.Meta;

public sealed interface RefValue extends Value {
  record Neu(LocalVar var, ImmutableSeq<Segment> spine) implements RefValue {
    public Neu(LocalVar var) {
      this(var, ImmutableSeq.empty());
    }
  }

  record Hole(Meta meta) implements RefValue {

  }

  sealed interface Segment {
    record Apply(Value arg, boolean explicit) implements Segment {}
  }
}

