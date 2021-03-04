// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.ref;

import org.aya.api.ref.Var;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record LocalVar(@NotNull String name) implements Var {
}

