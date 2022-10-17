// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import org.jetbrains.annotations.NotNull;

// non-Prop erased term can not appear in non-erased terms
public record ErasedTerm(@NotNull Term type, boolean isProp) implements Term {
}
