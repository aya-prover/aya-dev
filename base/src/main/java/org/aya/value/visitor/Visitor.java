// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.value.visitor;

import org.aya.value.FormValue;
import org.aya.value.IntroValue;
import org.aya.value.RefValue;
import org.jetbrains.annotations.NotNull;

public interface Visitor<P, R> {
  R visitPi(@NotNull FormValue.Pi pi, P p);
  R visitSigma(@NotNull FormValue.Sigma sigma, P p);
  R visitUnit(@NotNull FormValue.Unit unit, P p);
  R visitUniv(@NotNull FormValue.Univ univ, P p);
  R visitLam(@NotNull IntroValue.Lam lam, P p);
  R visitPair(@NotNull IntroValue.Pair pair, P p);
  R visitTT(@NotNull IntroValue.TT tt, P p);
  R visitNeu(@NotNull RefValue.Neu neu, P p);
  R visitFlex(@NotNull RefValue.Flex flex, P p);
}
