// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import org.aya.syntax.core.def.AnyDef;

/**
 * A marker for any definition variable, useful for conversion
 * into {@link AnyDef}.
 *
 * @see AnyDef#fromVar(AnyDefVar)
 */
public sealed interface AnyDefVar
  extends AnyVar permits DefVar, CompiledVar {
}
