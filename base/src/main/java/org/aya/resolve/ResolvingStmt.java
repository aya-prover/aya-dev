// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleContext;
import org.aya.syntax.concrete.stmt.Generalize;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// [Stmt] that is resolving, stores some extra information (i.e. the context 'inside' of it).
/// It is similar to the following agda code:
/// ```
/// postulate
///   Context : Set
/// inductive Stmt : Set where
///   FnDecl : Stmt
///   DataDecl : Stmt
///   DataCon : Stmt
/// inductive ExtInfo : Stmt -> Set where
///   ExtData : Context -> ExtInfo DataDecl
///   ExtFn : Context -> ExtInfo FnDecl
///   -- trivial extra info
///   ExtCon : ExtInfo DataCon
/// ResolvingStmt : Set _
/// ResolvingStmt = Σ[ s ∈ Stmt ] ExtInfo s
///```
public sealed interface ResolvingStmt {
  sealed interface ResolvingDecl extends ResolvingStmt { }

  record TopDecl(@NotNull Decl stmt, @NotNull Context context) implements ResolvingDecl { }
  record MiscDecl(@NotNull Decl stmt) implements ResolvingDecl { }
  record GenStmt(@NotNull Generalize stmt, @NotNull ModuleContext context) implements ResolvingStmt { }
  record ModStmt(@NotNull ImmutableSeq<@NotNull ResolvingStmt> resolved) implements ResolvingStmt { }
}
