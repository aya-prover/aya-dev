// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;

/**
 * @author ice1000
 */
public record DefVar<Def>(@NotNull Def def, @NotNull String name) implements Var {
}
