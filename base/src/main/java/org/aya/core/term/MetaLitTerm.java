// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple2;
import org.aya.core.def.GenericDef;
import org.aya.core.repr.ShapeRecognition;
import org.jetbrains.annotations.NotNull;

public record MetaLitTerm(
  @NotNull Object repr,
  @NotNull ImmutableSeq<Tuple2<GenericDef, ShapeRecognition>> candidates,
  @NotNull Term type
) implements StableWHNF {
}
