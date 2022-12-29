// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.jetbrains.annotations.NotNull;

/**
 * Elimination rules.
 *
 * @author ice1000
 */
public sealed interface Elimination extends Term permits AppTerm, PAppTerm, ProjTerm, OutTerm {

  @NotNull Term of();
}
