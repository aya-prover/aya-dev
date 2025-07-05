// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.ir.struct;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/// Represents the declaration site of a let-binding. This is factored out from the rest of the
/// IR because (I have free will) it might frequently subject to change as a result of
/// refactoring/optimization (e.g., might introduce )multiple parallel assignment to reduce IR
/// bloating or add extra semantics on bind site).
public record LetClause(@NotNull IrVarDecl decl, @NotNull ImmutableSeq<IrVal> app) { }
