// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

/**
 * Cubical-stable WHNF: those who will not change to other term formers
 * under face restrictions (aka cofibrations).
 */
public sealed interface StableWHNF permits
  CallTerm.Data,
  CallTerm.Struct,
  FormTerm.Path,
  FormTerm.Pi,
  FormTerm.Sigma,
  FormTerm.Univ,
  IntroTerm.Lambda,
  IntroTerm.New,
  IntroTerm.PathLam,
  IntroTerm.Tuple {
}
