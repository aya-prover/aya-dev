// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.struct;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/// Denotes a computation in monadic form, i.e., one of:
/// - Value
/// - Application
/// - Let binds
/// - Case/match
public interface IrComp {
  record Val(@NotNull IrVarRef ref) implements IrComp { }
  record App(@NotNull IrVal head, @NotNull ImmutableSeq<IrVal> args) implements IrComp { }
  record Let(@NotNull LetClause let, @NotNull IrComp body) implements IrComp { }
  // XXX: move match lambdas into inline form here
}
