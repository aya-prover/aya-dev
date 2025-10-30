// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.marker;

import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.core.term.xtt.EqTerm;

import java.util.function.UnaryOperator;

/// A marker interface that says this [Term] will introduce binding, such as holding a [org.aya.syntax.core.Closure].
/// Thus [#descent(UnaryOperator)] on these [Term] must be able to handle [Bound] term.
public sealed interface BindingIntro extends Term
  permits
  LamTerm, DepTypeTerm,
  CoeTerm, EqTerm,
  LetTerm,
  ClassCall, ClassCastTerm {
}
