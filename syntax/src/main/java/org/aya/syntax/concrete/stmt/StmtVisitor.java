// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.LazyValue;
import org.aya.generic.AyaDocile;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface StmtVisitor extends Consumer<Stmt> {
  record Type(@Nullable Expr userType, @NotNull LazyValue<@Nullable Term> lazyType) {
    public static final @NotNull Type noType = new Type((Expr) null);

    public Type(@NotNull LazyValue<@Nullable Term> lazyType) {
      this(null, lazyType);
    }

    public Type(@Nullable Expr userType) {
      this(userType, LazyValue.ofValue(null));
    }

    public @Nullable AyaDocile toDocile() {
      AyaDocile docile = lazyType.get();
      return docile == null ? userType : docile;
    }
  }

  /** @implNote Should conceptually only be used outside of these visitors, where types are all ignored. */
  @Deprecated
  @NotNull LazyValue<@Nullable Term> noType = LazyValue.ofValue(null);

  /// invoked when a module name is introduced, such as a module declaration or import-as
  default void visitModuleDecl(@NotNull SourcePos pos, @NotNull ModuleName path) { }
  /// module name ref
  default void visitModuleRef(@NotNull SourcePos pos, @NotNull ModuleName path) { }
  /// import
  default void visitModuleRef(@NotNull SourcePos pos, @NotNull ModulePath path) { }

  default void visitVar(
    @NotNull SourcePos pos, @NotNull AnyVar var,
    @NotNull Type type
  ) { }

  default void visitVarRef(
    @NotNull SourcePos pos, @NotNull AnyVar var,
    @NotNull Type type
  ) { visitVar(pos, var, type); }

  default void visitUnresolvedRef(@NotNull QualifiedID qid) { }

  default void visitVarDecl(
    @NotNull SourcePos pos, @NotNull AnyVar var,
    @NotNull Type type
  ) { visitVar(pos, var, type); }

  @ApiStatus.NonExtendable
  default void visitLocalVarDecl(@NotNull LocalVar var, @NotNull Type type) {
    visitVarDecl(var.definition(), var, type);
  }

  @ApiStatus.NonExtendable
  default void visitParamDecl(Expr.@NotNull Param param) {
    visitLocalVarDecl(param.ref(), fromParam(param));
  }

  private @Nullable Term varType(@Nullable AnyVar var) {
    if (var instanceof AnyDefVar defVar) {
      return TyckDef.defType(AnyDef.fromVar(defVar));
    }
    return null;
  }

  private @NotNull Type lazyType(@Nullable AnyVar var) {
    return new Type(null, LazyValue.of(() -> varType(var)));
  }

  default void visit(@NotNull BindBlock bb) {
    var t = Option.ofNullable(bb.resolvedTighters().get()).getOrElse(ImmutableSeq::empty);
    var l = Option.ofNullable(bb.resolvedLoosers().get()).getOrElse(ImmutableSeq::empty);
    t.forEachWith(bb.tighters(), (tt, b) -> visitVarRef(b.sourcePos(), tt, lazyType(tt)));
    l.forEachWith(bb.loosers(), (ll, b) -> visitVarRef(b.sourcePos(), ll, lazyType(ll)));
  }

  private void visitVars(@NotNull Stmt stmt) {
    switch (stmt) {
      case Generalize g -> g.variables.forEach(v -> visitVarDecl(v.sourcePos, v, new Type(v.owner.type.data())));
      case Command.Module m -> visitModuleDecl(m.sourcePos(), ModuleName.of(m.name()));
      case Command.Import i -> {
        // Essentially `i.asName() != null` but fancier
        var path = i.path();
        if (i.asName() instanceof WithPos(var pos, var asName)) {
          visitModuleRef(i.sourcePos(), path);
          visitModuleDecl(pos, ModuleName.of(asName));
        } else {
          if (i.sourcePosExceptLast() != SourcePos.NONE) visitModuleRef(i.sourcePosExceptLast(), path.dropLast(1));
          visitModuleDecl(i.sourcePosLast(), ModuleName.of(path.last()));
        }
      }
      case Command.Open o when o.fromSugar() -> { }  // handled in `case Decl` or `case Command.Import`
      case Command.Open o -> {
        visitModuleRef(o.sourcePos(), o.path());
        // TODO: what about the symbols that introduced by renaming
        // https://github.com/aya-prover/aya-dev/issues/721
        o.useHide().list().forEach(v -> visit(v.asBind()));
      }
      case Decl decl -> {
        visit(decl.bindBlock());
        visitVarDecl(decl.sourcePos(), decl.ref(), lazyType(decl.ref()));
        if (decl instanceof TeleDecl tele)
          tele.telescope.forEach(this::visitParamDecl);
      }
    }
  }

  default void accept(@NotNull Stmt stmt) {
    switch (stmt) {
      case Decl decl -> {
        if (decl instanceof TeleDecl tele) visitTelescopic(tele);
        switch (decl) {
          case DataDecl data -> data.body.forEach(this::accept);
          case ClassDecl clazz -> clazz.members.forEach(this);
          case FnDecl fn -> {
            fn.body.forEach(this::visitExpr, this::visitClause);
            if (fn.body instanceof FnBody.BlockBody block) {
              if (block.elims() != null) block.elims().forEachWith(block.rawElims(), (var, name) ->
                visitVarRef(name.sourcePos(), var, Type.noType));
            }
          }
          case DataCon con -> con.patterns.forEach(cl -> visitPattern(cl.term()));
          case PrimDecl _, ClassMember _ -> { }
        }
      }
      case Command command -> {
        switch (command) {
          case Command.Module module -> module.contents().forEach(this);
          case Command.Import _, Command.Open _ -> { }
        }
      }
      case Generalize generalize -> visitExpr(generalize.type);
    }
    visitVars(stmt);
  }

  // scope introducer
  default void visitLetBind(@NotNull Expr.LetBind bind) {

  }

  // scope introducer
  default void visitClause(@NotNull Pattern.Clause clause) {
    clause.forEach(this::visitExpr, this::visitPattern);
  }

  // TODO: maybe we can provide the corresponding Expr.Param or term/Param of this pattern.
  private void visitPattern(@NotNull WithPos<Pattern> pat) { visitPattern(pat.sourcePos(), pat.data()); }
  default void visitPattern(@NotNull SourcePos pos, @NotNull Pattern pat) {
    switch (pat) {
      case Pattern.Con con -> {
        var resolvedVar = con.resolved().data();
        visitVarRef(con.resolved().sourcePos(), AnyDef.toVar(resolvedVar),
          new Type(LazyValue.of(() -> TyckDef.defType(resolvedVar))));
      }
      case Pattern.Bind bind -> visitLocalVarDecl(bind.bind(), new Type(LazyValue.of(bind.type())));
      case Pattern.As as -> visitLocalVarDecl(as.as(), new Type(LazyValue.of(as.type())));
      default -> { }
    }

    pat.forEach(this::visitPattern);
  }

  private void visitExpr(@NotNull WithPos<Expr> expr) { visitExpr(expr.sourcePos(), expr.data()); }
  default void visitExpr(@NotNull SourcePos pos, @NotNull Expr expr) {
    switch (expr) {
      case Expr.Unresolved unresolved -> visitUnresolvedRef(unresolved.name());
      case Expr.Ref ref -> visitVarRef(pos, ref.var(), withTermType(ref));
      case Expr.Lambda lam -> visitLocalVarDecl(lam.ref(), Type.noType);
      case Expr.DepType depType -> visitParamDecl(depType.param());
      case Expr.Array array -> array.arrayBlock().forEach(
        left -> left.binds().forEach(bind -> visitLocalVarDecl(bind.var(), Type.noType)),
        _ -> { }
      );
      case Expr.Let let -> {
        var bind = let.bind();
        // it is possible that it has telescope without return type
        var hasType = bind.telescope().isNotEmpty() || !(bind.result().data() instanceof Expr.Hole);
        Type type;
        if (!hasType) {
          type = Type.noType;
        } else {
          var result = bind.result();
          // dummy pos, as we don't really need it.
          var piType = Expr.buildPi(SourcePos.NONE, bind.telescope().view(), result).data();
          type = new Type(piType);
        }

        visitLocalVarDecl(let.bind().bindName(), type);
      }
      case Expr.Do du -> du.binds().forEach(bind -> visitLocalVarDecl(bind.var(), Type.noType));
      case Expr.Proj proj when proj.ix().isRight() && proj.resolvedVar() != null ->
        visitVarRef(proj.ix().getRightValue().sourcePos(), proj.resolvedVar(), lazyType(proj.resolvedVar()));
      // TODO: use visitClause
      case Expr.Match match -> match.clauses().forEach(clause -> clause.patterns.forEach(
        t -> visitPattern(t.term())));
      default -> { }
    }

    expr.forEach(this::visitExpr);
  }

  default boolean enterScope() {

  }

  default void visitTelescopic(@NotNull TeleDecl telescopic) {
    telescopic.telescope.forEach(param -> param.forEach(this::visitExpr));
    if (telescopic.result != null) visitExpr(telescopic.result);
  }

  private @NotNull Type fromParam(@NotNull Expr.Param param) {
    return withTermType(param.type(), param);
  }

  private @NotNull Type withTermType(@NotNull Expr.WithTerm term) {
    return withTermType(null, term);
  }

  private @NotNull Type withTermType(@Nullable Expr userType, @NotNull Expr.WithTerm term) {
    return new Type(userType, LazyValue.of(term::coreType));
  }
}
