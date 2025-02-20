// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.marker;

import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.repr.MetaLitTerm;

/**
 * Things that are used in the middle of type checking, and will be removed afterward.
 * So, in the compiler, we expect these things to be thrown away.
 */
public sealed interface TyckInternal extends Term
  permits ErrorTerm, FreeTermLike, MetaPatTerm, MetaCall, MetaLitTerm {
}
