// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.aya.guest0x0.cubical.Partial;
import org.jetbrains.annotations.NotNull;

/**
 * Partial elements.
 *
 * @implNote I am unsure if this is stable as of our assumptions. Surely
 * a split partial may become a const partial, is that stable?
 */
public record PartialTerm(@NotNull Partial<Term> partial, @NotNull Term rhsType) implements Term {
}
