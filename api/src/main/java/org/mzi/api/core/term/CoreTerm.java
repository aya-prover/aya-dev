// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.core.term;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.util.NormalizeMode;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface CoreTerm {
  @NotNull CoreTerm normalize(@NotNull NormalizeMode mode);
  // TODO[kiva]: what in general does a term should have to expose to the outside world?
  //  ice: synthType, isType, etc.
}
