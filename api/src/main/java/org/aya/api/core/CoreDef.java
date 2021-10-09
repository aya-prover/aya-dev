// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.core;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.concrete.ConcreteDecl;
import org.aya.api.distill.AyaDocile;
import org.aya.api.ref.Bind;
import org.aya.api.ref.DefVar;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface CoreDef extends AyaDocile {
  @Contract(pure = true) @NotNull DefVar<? extends CoreDef, ? extends ConcreteDecl> ref();
  @NotNull CoreTerm result();
  @NotNull ImmutableSeq<? extends Bind> telescope();
}
