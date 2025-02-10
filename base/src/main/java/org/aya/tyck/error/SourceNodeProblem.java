// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import org.aya.util.position.SourceNode;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

public interface SourceNodeProblem extends Problem {
  @NotNull SourceNode expr();
  @Override default @NotNull SourcePos sourcePos() { return expr().sourcePos(); }
}
