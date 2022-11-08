// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

/**
 * Cubical-stable WHNF: those who will not change to other term formers
 * after a substitution (this usually happens under face restrictions (aka cofibrations)).
 */
public sealed interface StableWHNF extends Term
  permits DataCall, ErrorTerm, IntegerTerm, IntervalTerm, LamTerm, ListTerm, NewTerm, PLamTerm, PartialTyTerm, PathTerm, PiTerm, SigmaTerm, SortTerm, StringTerm, StructCall, TupTerm {
}
