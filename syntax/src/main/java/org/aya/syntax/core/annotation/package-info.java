// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

/// # Annotations
///
/// We use [org.aya.syntax.core.annotation.Bound] and [org.aya.syntax.core.annotation.Closed] to annotates the
/// term a method can handle/will return,
/// however, most places don't have clear db-closeness. For those places,
/// they mostly inherit from the inputs, for example, [org.aya.syntax.core.term.Term#elevate]
/// is a closed term if the term itself is already closed.
///
/// Most [org.aya.syntax.core.term.Term] data structures don't annotate the db-closeness of their sub-`Term`,
/// as they will be bound during tyck.
///
/// `@Bound` and `@Closed` may also apply to data structures with `Term`, such as `Jdg` or `Pat`.
///
/// @author hoshino
package org.aya.syntax.core.annotation;
