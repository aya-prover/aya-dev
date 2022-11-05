// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.SortKind;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record SigmaTerm(@NotNull ImmutableSeq<@NotNull Param> params) implements FormTerm, StableWHNF {
  public static @NotNull FormTerm.Sort calculateSigma(@NotNull FormTerm.Sort x, @NotNull FormTerm.Sort y) {
    int lift = Math.max(x.lift(), y.lift());
    if (x.kind() == SortKind.Prop || y.kind() == SortKind.Prop) {
      return Prop.INSTANCE;
    } else if (x.kind() == SortKind.Set || y.kind() == SortKind.Set) {
      return new Set(lift);
    } else if (x.kind() == SortKind.Type || y.kind() == SortKind.Type) {
      return new Type(lift);
    } else if (x instanceof ISet && y instanceof ISet) {
      // ice: this is controversial, but I think it's fine.
      // See https://github.com/agda/cubical/pull/910#issuecomment-1233113020
      return ISet.INSTANCE;
    }
    throw new AssertionError("unreachable");
  }
}
