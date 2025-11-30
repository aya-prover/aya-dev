// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

@Closed
public record FreeTerm(@NotNull LocalVar name) implements FreeTermLike {
  public static @Closed @NotNull FreeTerm of(@NotNull LocalVar name) {
    return new FreeTerm(name);
  }

  public static @NotNull ImmutableSeq<@Closed Term> dummy(int size) {
    return ImmutableSeq.fill(size, i -> new FreeTerm(new LocalVar("dummy" + i)));
  }

  public FreeTerm(@NotNull String name) { this(LocalVar.generate(name)); }

  @Override public @NotNull Term descent(@NotNull TermVisitor visitor) { return this; }
}
