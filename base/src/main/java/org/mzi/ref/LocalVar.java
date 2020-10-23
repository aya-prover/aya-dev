package org.mzi.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;

/**
 * @author ice1000
 */
public record LocalVar(@NotNull String name) implements Var {
}

