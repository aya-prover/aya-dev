// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.concrete;

import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.concrete.def.ConcreteDecl;
import org.mzi.api.error.Reporter;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.DefVar;
import org.mzi.api.util.Assoc;
import org.mzi.concrete.resolve.context.Context;
import org.mzi.core.def.DataDef;
import org.mzi.core.def.Def;
import org.mzi.core.def.FnDef;
import org.mzi.generic.Modifier;
import org.mzi.generic.Pat;
import org.mzi.tyck.StmtTycker;
import org.mzi.tyck.trace.Trace;

import java.util.EnumSet;
import java.util.Objects;

/**
 * concrete definition, corresponding to {@link org.mzi.core.def.Def}.
 *
 * @author re-xyr
 */
public sealed abstract class Decl extends SigItem implements Stmt, ConcreteDecl {
  public final @NotNull Accessibility accessibility;
  public final @NotNull ImmutableSeq<Stmt> abuseBlock;
  public @Nullable Context ctx = null;

  @Override public @NotNull Accessibility accessibility() {
    return accessibility;
  }

  protected Decl(
    @NotNull SourcePos sourcePos,
    @NotNull Accessibility accessibility,
    @NotNull ImmutableSeq<Stmt> abuseBlock,
    @NotNull ImmutableSeq<Expr.Param> telescope
  ) {
    super(sourcePos, telescope);
    this.accessibility = accessibility;
    this.abuseBlock = abuseBlock;
  }

  @Contract(pure = true) public abstract @NotNull DefVar<? extends Def, ? extends Decl> ref();

  protected abstract <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  public final <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret);
    return ret;
  }

  public final @Override <P, R> R doAccept(Stmt.@NotNull Visitor<P, R> visitor, P p) {
    return doAccept((Decl.Visitor<P, R>) visitor, p);
  }

  public final @Override <P, R> R doAccept(SigItem.@NotNull Visitor<P, R> visitor, P p) {
    return doAccept((Decl.Visitor<P, R>) visitor, p);
  }

  public Def tyck(@NotNull Reporter reporter, Trace.@Nullable Builder builder) {
    var tycker = new StmtTycker(reporter, builder);
    return accept(tycker, tycker.newTycker());
  }

  public interface Visitor<P, R> {
    default void traceEntrance(@NotNull Decl decl, P p) {
    }
    default void traceExit(R r) {
    }
    R visitDataDecl(@NotNull Decl.DataDecl decl, P p);
    R visitFnDecl(@NotNull Decl.FnDecl decl, P p);
  }

  public static final class DataCtor extends SigItem {
    public @NotNull DefVar<DataDef.Ctor, Decl.DataCtor> ref;
    public @NotNull Buffer<String> elim;
    public @NotNull Buffer<Pat.Clause<Expr>> clauses;
    public boolean coerce;

    public DataCtor(@NotNull SourcePos sourcePos,
                    @NotNull String name,
                    @NotNull ImmutableSeq<Expr.Param> telescope,
                    @NotNull Buffer<String> elim,
                    @NotNull Buffer<Pat.Clause<Expr>> clauses,
                    boolean coerce) {
      super(sourcePos, telescope);
      this.elim = elim;
      this.clauses = clauses;
      this.coerce = coerce;
      this.ref = DefVar.concrete(this, name);
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DataCtor dataCtor = (DataCtor) o;
      return coerce == dataCtor.coerce && telescope.equals(dataCtor.telescope) && elim.equals(dataCtor.elim) && clauses.equals(dataCtor.clauses);
    }

    @Override
    public int hashCode() {
      return Objects.hash(telescope, elim, clauses, coerce);
    }

    @Override protected <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCtor(this, p);
    }

    @Override public @NotNull DefVar<DataDef.Ctor, DataCtor> ref() {
      return ref;
    }
  }

  public sealed interface DataBody {
    record Ctors(
      @NotNull Buffer<DataCtor> ctors
    ) implements DataBody {
      @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
        return visitor.visitCtor(this, p);
      }
    }

    record Clauses(
      @NotNull Buffer<String> elim,
      @NotNull Buffer<Tuple2<Pat<Expr>, DataCtor>> clauses
    ) implements DataBody {
      @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
        return visitor.visitClause(this, p);
      }
    }

    interface Visitor<P, R> {
      R visitCtor(@NotNull Ctors ctors, P p);
      R visitClause(@NotNull Clauses clauses, P p);
    }

    <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  }

  /**
   * Concrete data definition
   *
   * @author kiva
   * @see DataDef
   */
  public static final class DataDecl extends Decl {
    public final @NotNull DefVar<DataDef, DataDecl> ref;
    public @NotNull Expr result;
    public @NotNull DataBody body;

    public DataDecl(
      @NotNull SourcePos sourcePos,
      @NotNull Accessibility accessibility,
      @NotNull String name,
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull DataBody body,
      @NotNull ImmutableSeq<Stmt> abuseBlock
    ) {
      super(sourcePos, accessibility, abuseBlock, telescope);
      this.result = result;
      this.body = body;
      this.ref = DefVar.concrete(this, name);
    }

    @Override
    protected <P, R> R doAccept(@NotNull Decl.Visitor<P, R> visitor, P p) {
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
  }

  /**
   * Concrete function definition
   *
   * @author re-xyr
   * @see FnDef
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
      @NotNull ImmutableSeq<Expr.Param> telescope,
      @NotNull Expr result,
      @NotNull Expr body,
      @NotNull ImmutableSeq<Stmt> abuseBlock
    ) {
      super(sourcePos, accessibility, abuseBlock, telescope);
      this.modifiers = modifiers;
      this.assoc = assoc;
      this.ref = DefVar.concrete(this, name);
      this.result = result;
      this.body = body;
    }

    @Override
    protected <P, R> R doAccept(@NotNull Decl.Visitor<P, R> visitor, P p) {
      return visitor.visitFnDecl(this, p);
    }

    @Override public @NotNull DefVar<FnDef, FnDecl> ref() {
      return this.ref;
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
  }
}
