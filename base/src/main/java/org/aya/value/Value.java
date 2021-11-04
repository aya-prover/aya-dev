// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.value;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.LocalVar;
import org.aya.value.visitor.Visitor;
import org.jetbrains.annotations.NotNull;

public sealed interface Value permits FormValue, IntroValue, RefValue {
  <P, R> R accept(Visitor<P, R> visitor, P p);

  sealed interface Segment {
    record Apply(Arg arg) implements Segment {
      public Apply(Value value, boolean explicit) {
        this(new Arg(value, explicit));
      }
    }

    record ProjL() implements Segment {
    }

    record ProjR() implements Segment {
    }
  }

  default Value apply(Arg arg) {
    // TODO: report error
    return null;
  }

  default Value projL() {
    // TODO: report error
    return null;
  }

  default Value projR() {
    // TODO: report error
    return null;
  }

  default Value elim(@NotNull ImmutableSeq<Segment> spine) {
    return spine.foldLeft(this, (value, segment) -> switch (segment) {
      case Segment.Apply app -> this.apply(app.arg());
      case Segment.ProjL ignored -> this.projL();
      case Segment.ProjR ignored -> this.projR();
    });
  }

  record Param(@NotNull LocalVar ref, @NotNull Value type, boolean explicit) {
    public Param(@NotNull LocalVar ref, @NotNull Value type) {
      this(ref, type, true);
    }
  }

  record Arg(@NotNull Value value, boolean explicit) {
  }
}
