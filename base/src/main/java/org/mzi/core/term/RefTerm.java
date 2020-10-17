package org.mzi.core.term;

import org.jetbrains.annotations.NotNull;
import org.mzi.core.ref.Ref;

public record RefTerm(@NotNull Ref ref) implements Term {
}
