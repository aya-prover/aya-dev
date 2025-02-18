// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import org.aya.syntax.core.Jdg;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

/// TODO: do we really need the whole [Jdg] ?
public record LetFreeTerm(@Override @NotNull LocalVar name, @NotNull Jdg definedAs) implements FreeTermLike {
}
