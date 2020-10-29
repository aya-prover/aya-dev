// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import asia.kala.Tuple2;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.SourcePos;
import org.mzi.generic.Assoc;
import org.mzi.generic.Modifier;
import org.mzi.ref.DefVar;

import java.util.EnumSet;

/**
 * concrete definition, corresponding to {@link org.mzi.core.def.Def}.
 *
 * @author re-xyr
 */
public sealed interface Decl extends Stmt {
  @Contract(pure = true) @NotNull DefVar<? extends Decl> ref();

  sealed interface DataBody {
    record DataCtor(
      @NotNull String name,
      @NotNull Buffer<Param> telescope,
      @NotNull Buffer<String> elim,
      @NotNull Buffer<Clause> clauses,
      boolean coerce
    ) implements DataBody {}

    record DataClause(
      @NotNull Buffer<String> elim,
      @NotNull Buffer<Tuple2<Pattern, DataCtor>> clauses
    ) implements DataBody {}
  }

  /**
   * concrete data definition
   *
   * @author kiva
   */
  final class DataDecl implements Decl {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull DefVar<DataDecl> ref;
    public final @NotNull Buffer<Param> telescope;
    public final @NotNull Expr result;
    public final @NotNull Buffer<DataBody> body;
    public final @NotNull Buffer<Stmt> abuseBlock;

    public DataDecl(
      @NotNull SourcePos sourcePos,
      @NotNull String name,
      @NotNull Buffer<Param> telescope,
      @NotNull Expr result,
      @NotNull Buffer<DataBody> body,
      @NotNull Buffer<Stmt> abuseBlock
    ) {
      this.sourcePos = sourcePos;
      this.telescope = telescope;
      this.result = result;
      this.body = body;
      this.abuseBlock = abuseBlock;
      this.ref = new DefVar<>(this, name);
    }

    @Override
    public @NotNull DefVar<? extends Decl> ref() {
      return this.ref;
    }

    @Override
    public @NotNull SourcePos sourcePos() {
      return this.sourcePos;
    }
  }

  /**
   * concrete function definition, corresponding to {@link org.mzi.core.def.FnDef}.
   *
   * @author re-xyr
   */
  final class FnDecl implements Decl {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull EnumSet<Modifier> modifiers;
    public final @Nullable Assoc assoc;
    public final @NotNull DefVar<FnDecl> ref;
    public final @NotNull Buffer<Param> telescope;
    public final @NotNull Expr result;
    public final @NotNull Expr body;
    public final @NotNull Buffer<Stmt> abuseBlock;

    public FnDecl(
      @NotNull SourcePos sourcePos,
      @NotNull EnumSet<Modifier> modifiers,
      @Nullable Assoc assoc,
      @NotNull String name,
      @NotNull Buffer<Param> telescope,
      @NotNull Expr result,
      @NotNull Expr body,
      @NotNull Buffer<Stmt> abuseBlock
    ) {
      this.sourcePos = sourcePos;
      this.modifiers = modifiers;
      this.assoc = assoc;
      this.ref = new DefVar<>(this, name);
      this.telescope = telescope;
      this.result = result;
      this.body = body;
      this.abuseBlock = abuseBlock;
    }

    public @NotNull SourcePos sourcePos() {
      return this.sourcePos;
    }

    public @NotNull DefVar<FnDecl> ref() {
      return this.ref;
    }
  }
}
