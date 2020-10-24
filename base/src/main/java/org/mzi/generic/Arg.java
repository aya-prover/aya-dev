// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.generic;

import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;

/**
 * @author ice1000
 * @param <T> the type of expressions, can be {@link org.mzi.core.term.Term} or {@link Expr}.
 */
public record Arg<T>(@NotNull T term, boolean explicit) {
}
