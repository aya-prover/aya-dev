// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.core.def.CoreDef;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Signatured;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface Def extends CoreDef {
  @Override @NotNull DefVar<? extends Def, ? extends Signatured> ref();

  <P, R> R accept(Visitor<P, R> visitor, P p);

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> {
    R visitFn(@NotNull FnDef def, P p);
    R visitData(@NotNull DataDef def, P p);
    R visitCtor(DataDef.@NotNull Ctor def, P p);
  }
}
