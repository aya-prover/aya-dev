package org.mzi.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;

/**
 * @author ice1000
 */
public record DefVar<Def>(@NotNull Def def, @NotNull String name) implements Var {
}
