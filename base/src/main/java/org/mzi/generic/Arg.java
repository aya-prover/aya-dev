package org.mzi.generic;

import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;

/**
 * @author ice1000
 * @param <T> the type of expressions, can be {@link org.mzi.core.term.Term} or {@link Expr}.
 */
public record Arg<T>(@NotNull T term, boolean explicit) {
}
