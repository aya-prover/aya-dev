// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * @author zaoqi
 */
public sealed interface GenericDef extends AyaDocile permits ClassDef, Def {
  @NotNull DefVar<?, ?> ref();

  @NotNull Term result();

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> {
    R visitFn(@NotNull FnDef def, P p);
    R visitData(@NotNull DataDef def, P p);
    R visitCtor(@NotNull CtorDef def, P p);
    R visitStruct(@NotNull StructDef def, P p);
    R visitField(@NotNull FieldDef def, P p);
    R visitPrim(@NotNull PrimDef def, P p);
  }
}
