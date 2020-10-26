// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.def;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.core.def.CoreDef;
import org.mzi.ref.DefVar;

/**
 * @author ice1000
 */
public interface Def extends CoreDef {
  @Override @NotNull DefVar<? extends Def> ref();
}
