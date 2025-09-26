// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.annotation;

import org.aya.syntax.core.term.Term;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Annotations whether a [Term] is a (dbi-)closed term, which means it has **NO** uncaptured [org.aya.syntax.core.term.LocalTerm], thus
/// it is dbi-closed.
///
/// @see Bound
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Closed {
}
