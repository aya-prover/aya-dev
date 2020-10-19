package org.mzi.ref;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Ref;

/**
 * @author ice1000
 */
public record LocalRef(@NotNull String name) implements Ref {
}

