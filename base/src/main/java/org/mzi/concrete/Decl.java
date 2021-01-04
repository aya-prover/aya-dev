// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.glavo.kala.Tuple2;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.SourcePos;
import org.mzi.api.util.Assoc;
import org.mzi.generic.Modifier;
import org.mzi.api.ref.DefVar;

import java.util.EnumSet;
import java.util.Objects;

/**
 * concrete definition, corresponding to {@link org.mzi.core.def.Def}.
 *
 * @author re-xyr
 */
public sealed interface Decl extends Stmt {
  @Contract(pure = true) @NotNull DefVar<? extends Decl> ref();

  record DataCtor(
    @NotNull String name,
    @NotNull Buffer<Param> telescope,
    @NotNull Buffer<String> elim,
    @NotNull Buffer<Clause> clauses,
    boolean coerce
  ) {
  }

  sealed interface DataBody {
    record Ctors(
      @NotNull Buffer<DataCtor> ctors
    ) implements DataBody {}

    record Clauses(
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
    public final @NotNull Accessibility accessibility;
    public final @NotNull DefVar<DataDecl> ref;
    public final @NotNull Buffer<Param> telescope;
    public @NotNull Expr result;
    public @NotNull DataBody body;
    public final @NotNull Buffer<Stmt> abuseBlock;

    public DataDecl(
      @NotNull SourcePos sourcePos,
      @NotNull Accessibility accessibility,
      @NotNull String name,
      @NotNull Buffer<Param> telescope,
      @NotNull Expr result,
      @NotNull DataBody body,
      @NotNull Buffer<Stmt> abuseBlock
    ) {
      this.sourcePos = sourcePos;
      this.accessibility = accessibility;
      this.telescope = telescope;
      this.result = result;
      this.body = body;
      this.abuseBlock = abuseBlock;
      this.ref = new DefVar<>(this, name);
    }

    @Override
    public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitDataDecl(this, p);
    }

    @Override
    public @NotNull DefVar<DataDecl> ref() {
      return this.ref;
    }

    @Override
    public @NotNull Accessibility accessibility() {
      return this.accessibility;
    }

    @Override
    public @NotNull SourcePos sourcePos() {
      return this.sourcePos;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DataDecl dataDecl)) return false;
      return sourcePos.equals(dataDecl.sourcePos) &&
        telescope.equals(dataDecl.telescope) &&
        result.equals(dataDecl.result) &&
        body.equals(dataDecl.body) &&
        abuseBlock.equals(dataDecl.abuseBlock);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sourcePos, telescope, result, body, abuseBlock);
    }

    @Override public String toString() {
      return "DataDecl{" +
        "sourcePos=" + sourcePos +
        ", accessibility=" + accessibility +
        ", telescope=" + telescope +
        ", result=" + result +
        ", body=" + body +
        ", abuseBlock=" + abuseBlock +
        '}';
    }
  }

  /**
   * concrete function definition, corresponding to {@link org.mzi.core.def.FnDef}.
   *
   * @author re-xyr
   */
  final class FnDecl implements Decl {
    public final @NotNull SourcePos sourcePos;
    public final @NotNull Accessibility accessibility;
    public final @NotNull EnumSet<Modifier> modifiers;
    public final @Nullable Assoc assoc;
    public final @NotNull DefVar<FnDecl> ref;
    public final @NotNull Buffer<Param> telescope;
    public @Nullable Expr result;
    public @NotNull Expr body;
    public final @NotNull Buffer<Stmt> abuseBlock;

    public FnDecl(
      @NotNull SourcePos sourcePos,
      @NotNull Accessibility accessibility,
      @NotNull EnumSet<Modifier> modifiers,
      @Nullable Assoc assoc,
      @NotNull String name,
      @NotNull Buffer<Param> telescope,
      @NotNull Expr result,
      @NotNull Expr body,
      @NotNull Buffer<Stmt> abuseBlock
    ) {
      this.sourcePos = sourcePos;
      this.accessibility = accessibility;
      this.modifiers = modifiers;
      this.assoc = assoc;
      this.ref = new DefVar<>(this, name);
      this.telescope = telescope;
      this.result = result;
      this.body = body;
      this.abuseBlock = abuseBlock;
    }

    @Override
    public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitFnDecl(this, p);
    }

    @Override
    public @NotNull SourcePos sourcePos() {
      return this.sourcePos;
    }

    @Override
    public @NotNull DefVar<FnDecl> ref() {
      return this.ref;
    }

    @Override
    public @NotNull Accessibility accessibility() { return this.accessibility; }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FnDecl fnDecl)) return false;
      return sourcePos.equals(fnDecl.sourcePos) &&
        modifiers.equals(fnDecl.modifiers) &&
        assoc == fnDecl.assoc &&
        telescope.equals(fnDecl.telescope) &&
        Objects.equals(result, fnDecl.result) &&
        body.equals(fnDecl.body) &&
        abuseBlock.equals(fnDecl.abuseBlock);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sourcePos, modifiers, assoc, telescope, result, body, abuseBlock);
    }

    @Override public String toString() {
      return "FnDecl{" +
        "sourcePos=" + sourcePos +
        ", accessibility=" + accessibility +
        ", modifiers=" + modifiers +
        ", assoc=" + assoc +
        ", telescope=" + telescope +
        ", result=" + result +
        ", body=" + body +
        ", abuseBlock=" + abuseBlock +
        '}';
    }
  }
}
