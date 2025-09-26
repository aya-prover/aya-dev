// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.annotation;

import org.aya.syntax.core.term.Term;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Annotations whether a [Term] is a bound term, which means it **MAY** have uncaptured [org.aya.syntax.core.term.LocalTerm].
/// This basically a [Term] version of [org.jetbrains.annotations.Nullable].
///
/// @see Closed
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Bound {
}
