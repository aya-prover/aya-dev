// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

/**
 * Term formers.
 */
public sealed interface Formation extends Term
  permits DataCall, IntervalTerm, PartialTyTerm, PathTerm, PiTerm, SigmaTerm, StructCall, SubTerm {
}
