package org.mzi.generic;

import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 * @param <T> the type of expressions, can be {@link org.mzi.core.term.Term} or {@link org.mzi.concrete.term.Expr}.
 */
public record Arg<T>(@NotNull T term, boolean explicit) {
}
