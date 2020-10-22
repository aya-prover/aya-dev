package org.mzi.api.util;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.SourcePos;

/**
 * @author kiva
 */
public record Ident(@NotNull String id, @NotNull SourcePos pos) {
}
