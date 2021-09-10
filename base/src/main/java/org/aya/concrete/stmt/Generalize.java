// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.util.WithPos;
import org.jetbrains.annotations.NotNull;

public sealed interface Generalize extends Stmt {
  @Override default @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  record Levels(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<WithPos<LevelGenVar>> levels
  ) implements Generalize {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLevels(this, p);
    }
  }
}
