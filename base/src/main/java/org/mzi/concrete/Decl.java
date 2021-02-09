// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.glavo.kala.Tuple2;
import org.glavo.kala.Unit;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.concrete.def.ConcreteDecl;
import org.mzi.api.error.Reporter;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.DefVar;
import org.mzi.api.util.Assoc;
import org.mzi.core.def.DataDef;
import org.mzi.core.def.Def;
import org.mzi.core.def.FnDef;
import org.mzi.generic.Modifier;
import org.mzi.tyck.StmtTycker;

import java.util.EnumSet;
import java.util.Objects;

/**
 * concrete definition, corresponding to {@link org.mzi.core.def.Def}.
 *
 * @author re-xyr
 */
public sealed abstract class Decl implements Stmt, ConcreteDecl {
  public final @NotNull SourcePos sourcePos;
  public final @NotNull Accessibility accessibility;
  public final @NotNull Buffer<Stmt> abuseBlock;

  // will change after resolve
  public @NotNull Buffer<Param> telescope;

  @Override public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  @Override public @NotNull Accessibility accessibility() {
    return accessibility;
  }

  protected Decl(
    @NotNull SourcePos sourcePos,
    @NotNull Accessibility accessibility,
    @NotNull Buffer<Stmt> abuseBlock,
    @NotNull Buffer<Param> telescope
  ) {
    this.sourcePos = sourcePos;
    this.accessibility = accessibility;
    this.abuseBlock = abuseBlock;
    this.telescope = telescope;
  }

  @Contract(pure = true) public abstract @NotNull DefVar<? extends Def, ? extends Decl> ref();

  abstract <P, R> R accept(Decl.@NotNull Visitor<P, R> visitor, P p);

  public final @Override <P, R> R accept(Stmt.@NotNull Visitor<P, R> visitor, P p) {
    return accept((Decl.Visitor<P, R>) visitor, p);
  }

  public Def tyck(@NotNull Reporter reporter) {
    return accept(new StmtTycker(reporter), Unit.unit());
  }

  public interface Visitor<P, R> {
    R visitDataDecl(@NotNull Decl.DataDecl decl, P p);
    R visitFnDecl(@NotNull Decl.FnDecl decl, P p);
  }

  public static record DataCtor(
    @NotNull String name,
    @NotNull Buffer<Param> telescope,
    @NotNull Buffer<String> elim,
    @NotNull Buffer<Clause> clauses,
    boolean coerce
  ) {
  }

  public sealed interface DataBody {
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
  public static final class DataDecl extends Decl {
    public final @NotNull DefVar<DataDef, DataDecl> ref;
    public @NotNull Expr result;
    public @NotNull DataBody body;

    public DataDecl(
      @NotNull SourcePos sourcePos,
      @NotNull Accessibility accessibility,
      @NotNull String name,
      @NotNull Buffer<Param> telescope,
      @NotNull Expr result,
      @NotNull DataBody body,
      @NotNull Buffer<Stmt> abuseBlock
    ) {
      super(sourcePos, accessibility, abuseBlock, telescope);
      this.result = result;
      this.body = body;
      this.ref = DefVar.concrete(this, name);
    }

    @Override
    public <P, R> R accept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitDataDecl(this, p);
    }

    @Override public @NotNull DefVar<DataDef, DataDecl> ref() {
      return this.ref;
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
  public static final class FnDecl extends Decl {
    public final @NotNull EnumSet<Modifier> modifiers;
    public final @Nullable Assoc assoc;
    public final @NotNull DefVar<FnDef, FnDecl> ref;
    public @NotNull Expr result;
    public @NotNull Expr body;

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
      super(sourcePos, accessibility, abuseBlock, telescope);
      this.modifiers = modifiers;
      this.assoc = assoc;
      this.ref = DefVar.concrete(this, name);
      this.result = result;
      this.body = body;
    }

    @Override
    public <P, R> R accept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitFnDecl(this, p);
    }

    @Override
    public @NotNull SourcePos sourcePos() {
      return this.sourcePos;
    }

    @Override public @NotNull DefVar<FnDef, FnDecl> ref() {
      return this.ref;
    }

    @Override
    public @NotNull Accessibility accessibility() {
      return this.accessibility;
    }

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
