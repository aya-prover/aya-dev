// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.core;

import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface CoreTerm extends Docile {
  int findUsages(@NotNull Var var);
  @NotNull CoreTerm normalize(@NotNull NormalizeMode mode);
  @NotNull CorePat toPat();
  // TODO[kiva]: what in general does a term should have to expose to the outside world?
  //  ice: synthType, isType, etc.
}
