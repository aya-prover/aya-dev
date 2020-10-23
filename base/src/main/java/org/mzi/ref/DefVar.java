package org.mzi.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;
import org.mzi.core.def.Def;

/**
 * @author ice1000
 */
public record DefVar(@NotNull Def def, @NotNull String name) implements Var {
}
