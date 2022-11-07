// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

/**
 * Cubical-stable WHNF: those who will not change to other term formers
 * under face restrictions (aka cofibrations).
 */
public sealed interface StableWHNF extends Term permits DataCall, StructCall, ErrorTerm, PathTerm, PiTerm, SigmaTerm, SortTerm, IntegerTerm, IntervalTerm, LamTerm, NewTerm, PLamTerm, TupTerm, ListTerm, StringTerm {
}
