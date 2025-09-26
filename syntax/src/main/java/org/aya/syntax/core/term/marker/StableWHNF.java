// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.marker;

import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.core.term.repr.StringTerm;
import org.aya.syntax.core.term.xtt.*;

/**
 * Cubical-stable WHNF: those who will not change to other term formers
 * after a substitution (this usually happens under face restrictions (aka cofibrations)).
 */
public sealed interface StableWHNF extends Term
  permits ClassCastTerm, DepTypeTerm, ErrorTerm, LamTerm, NewTerm, SortTerm, TupTerm, ClassCall, DataCall, IntegerTerm, ListTerm, StringTerm, DimTerm, DimTyTerm, EqTerm, PartialTerm, PartialTyTerm {
}
