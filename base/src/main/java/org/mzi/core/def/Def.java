// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.def;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.core.def.CoreDef;
import org.mzi.api.ref.DefVar;
import org.mzi.concrete.Decl;

/**
 * @author ice1000
 */
public interface Def extends CoreDef {
  @Override @NotNull DefVar<? extends Def, ? extends Decl> ref();

  <P, R> R accept(Visitor<P, R> visitor, P p);

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> {
    R visitFn(@NotNull FnDef def, P p);
    R visitData(@NotNull DataDef def, P p);
  }
}
