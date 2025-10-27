// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.annotation;

import org.aya.syntax.core.term.LocalTerm;
import org.aya.syntax.core.term.Term;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Annotations whether a [Term] is a bound/dbi-open term, which means it **MAY** have uncaptured [LocalTerm].
/// This basically a [Term] version of [org.jetbrains.annotations.Nullable].
///
/// ## What kind of operations are considered [Bound]-unfriendly?
///
/// Any operation that moves [Term] to a higher/lower level, for example, descent and normalize term `fn => (fn => ^1 ^0) 0`
/// is considered not [Bound]-friendly, as it becomes `fn => ^1 0`, which is ill-scoped.
/// Here is a table of [Bound]-friendly of common operations:
///
///  Operation                                  | Is [Bound]-friendly?
/// --------------------------------------------|----------------------
///  Normalize                                  | false[^1]
///  Typechecking(inherit)                      | false
///  Zonk                                       | true
///  Pretty print                               | true
///  Instantiate `i`th index with [Closed] term | only if no other indices are involved (only if `i` is the outermost index)
///  Bind to an unoccupied index                | true
///  PatMatcher                                 | false
///
/// [^1]: Although normalizer **can** safely normalize [Bound] term,
///       but we let it fails on [LocalTerm] to reveal potential bugs.
///       Thus, operations that depend on normalizer become not [Bound]-friendly.
///
/// @see Closed
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE_USE, ElementType.METHOD, ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
public @interface Bound {
}
