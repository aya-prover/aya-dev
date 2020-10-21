package org.mzi.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;
import org.mzi.core.def.Def;

/**
 * @author ice1000
 */
public record DefRef(@NotNull Def def, @NotNull String name) implements Ref {
}
