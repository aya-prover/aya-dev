// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableMap;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.FieldDef;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
public record NewTerm(
  @NotNull StructCall struct,
  @NotNull ImmutableMap<DefVar<FieldDef, TeleDecl.StructField>, Term> params
) implements StableWHNF {
}
