// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.def.Def;
import org.aya.core.def.GenericDef;
import org.jetbrains.annotations.NotNull;

/** CheckedTriConsumer */
@FunctionalInterface
public interface ModuleCallback<E extends Exception> {
  void onModuleTycked(@NotNull ResolveInfo moduleResolve, @NotNull ImmutableSeq<GenericDef> defs)
    throws E;
}
