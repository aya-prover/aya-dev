// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.jetbrains.annotations.NotNull;

/// Modifiers have semantics in core, and are boolean-valued.
/// If a "modifier"-like thing is only for surface or type checking,
/// it should be an annotation.
///
/// @author kiva
public enum Modifier {
  /// Denotes that a function's invocations are never reduced.
  /// Useful in debugging, when you really don't wanna see the full NF.
  Opaque("opaque"),
  /// Denotes that a function's invocations are eagerly reduced.
  Inline("inline"),
  /// That this function uses overlapping and order-insensitive
  /// pattern matching semantics.
  Overlap("overlap"),
  /// That the function does not need termination checking,
  /// and will not be reduced.
  NonTerminating("nonterminating"),
  /// That the function is enforced to be tail recursive.
  Tailrec("tailrec")
  ;

  public final @NotNull String keyword;

  Modifier(@NotNull String keyword) { this.keyword = keyword; }
}
