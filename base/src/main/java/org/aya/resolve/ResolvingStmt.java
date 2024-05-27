// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.context.Context;
import org.aya.syntax.concrete.stmt.Generalize;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.jetbrains.annotations.NotNull;

/**
 * {@link Stmt} that is resolving, stores some extra information (i.e. the context 'inside' of it).
 * This is a functional construction, it is similar to the following agda code:
 *
 * <pre>
 *   postulate
 *     Context : Set
 *
 *   data Stmt : Set where
 *     FnDecl : Stmt
 *     DataDecl : Stmt
 *     DataCon : Stmt
 *
 *   data ExtInfo : Stmt -> Set where
 *     ExtData : Context -> ExtInfo DataDecl
 *     ExtFn : Context -> ExtInfo FnDecl
 *     -- trivial extra info
 *     ExtCon : ExtInfo DataCon
 *
 *   ResolvingStmt : Set _
 *   ResolvingStmt = Σ[ s ∈ Stmt ] ExtInfo s
 * </pre>
 * <p>
 */
public sealed interface ResolvingStmt {
  sealed interface ResolvingDecl extends ResolvingStmt { }

  record TopDecl(@NotNull Decl stmt, @NotNull Context context) implements ResolvingDecl { }
  record MiscDecl(@NotNull Decl stmt) implements ResolvingDecl { }
  record GenStmt(@NotNull Generalize stmt) implements ResolvingStmt { }
  record ModStmt(@NotNull ImmutableSeq<@NotNull ResolvingStmt> resolved) implements ResolvingStmt { }
}
