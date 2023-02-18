// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.tyck.pat;

import kala.collection.Seq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.jetbrains.annotations.NotNull;

/**
 * Used in pattern classifier.
 *
 * @author ice1000
 */
public record Indexed<Pat>(@NotNull Pat pat, int ix) {
  public static @NotNull ImmutableIntSeq indices(@NotNull Seq<? extends Indexed<?>> cls) {
    return cls.view().mapToInt(ImmutableIntSeq.factory(), Indexed::ix);
  }
}
