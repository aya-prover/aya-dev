// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.LazyValue;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface StmtVisitor extends Consumer<Stmt> {
  /** module decl or import as name */
  default void visitModuleDecl(@NotNull SourcePos pos, @NotNull ModuleName path) { }
  /** module name ref */
  default void visitModuleRef(@NotNull SourcePos pos, @NotNull ModuleName path) { }
  /** import */
  default void visitModuleRef(@NotNull SourcePos pos, @NotNull ModulePath path) { }
  default void visitVar(
    @NotNull SourcePos pos, @NotNull AnyVar var,
    @NotNull LazyValue<@Nullable Term> type
  ) { }
  default void visitVarRef(
    @NotNull SourcePos pos, @NotNull AnyVar var,
    @NotNull LazyValue<@Nullable Term> type
  ) { visitVar(pos, var, type); }
  default void visitVarDecl(
    @NotNull SourcePos pos, @NotNull AnyVar var,
    @NotNull LazyValue<@Nullable Term> type
  ) { visitVar(pos, var, type); }

  private @Nullable Term varType(@Nullable AnyVar var) {
    if (var instanceof DefVar<?, ?> defVar) {
      var signature = defVar.signature;
      if (signature != null) return signature.makePi();
    }
    return null;
  }

  private @NotNull LazyValue<@Nullable Term> lazyType(@Nullable AnyVar var) {
    return LazyValue.of(() -> varType(var));
  }

  /** @implNote Should conceptually only be used outside of these visitors, where types are all ignored. */
  @NotNull LazyValue<@Nullable Term> noType = LazyValue.ofValue(null);
  default void visit(@NotNull BindBlock bb) {
    var t = Option.ofNullable(bb.resolvedTighters().get()).getOrElse(ImmutableSeq::empty);
    var l = Option.ofNullable(bb.resolvedLoosers().get()).getOrElse(ImmutableSeq::empty);
    t.forEachWith(bb.tighters(), (tt, b) -> visitVarRef(b.sourcePos(), tt, lazyType(tt)));
    l.forEachWith(bb.loosers(), (ll, b) -> visitVarRef(b.sourcePos(), ll, lazyType(ll)));
  }

  private void visitVars(@NotNull Stmt stmt) {
    switch (stmt) {
      case Generalize g -> g.variables.forEach(v -> visitVarDecl(v.sourcePos, v, noType));
      case Command.Module m -> visitModuleDecl(m.sourcePos(), new ModuleName.Qualified(m.name()));
      case Command.Import i -> {
        var isAlsoDef = i.asName() == null;
        visitModuleRef(i.sourcePos(), i.path());
        if (!isAlsoDef) {
          visitModuleDecl(i.sourcePos(), new ModuleName.Qualified(i.asName()));
        } else {
          // TODO: visitModuleDecl on the last element of i.path
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
        decl.telescope.forEach(p -> visitVarDecl(p.sourcePos(), p.ref(), withTermType(p)));
      }
    }
  }

  default void accept(@NotNull Stmt stmt) {
    switch (stmt) {
      case Decl decl -> {
        visitTelescopic(decl);
        switch (decl) {
          case DataDecl data -> data.body.forEach(this);
          // case ClassDecl clazz -> clazz.members.forEach(this);
          case FnDecl fn -> fn.body.forEach(this::visitExpr, cl -> cl.forEach(this::visitExpr,
            this::visitPattern));
          case DataCon con -> con.patterns.forEach(cl -> visitPattern(cl.term()));
          // case TeleDecl.ClassMember field -> field.body = field.body.map(this);
          case PrimDecl _ -> { }
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

  private void visitPattern(@NotNull WithPos<Pattern> pat) { visitPattern(pat.sourcePos(), pat.data()); }
  default void visitPattern(@NotNull SourcePos pos, @NotNull Pattern pat) {
    switch (pat) {
      case Pattern.Con con -> {
        var resolvedVar = con.resolved().data();
        visitVarRef(con.resolved().sourcePos(), resolvedVar, lazyType(resolvedVar));
      }
      case Pattern.Bind bind -> visitVarDecl(pos, bind.bind(), LazyValue.of(bind.type()));
      case Pattern.As as -> visitVarDecl(as.as().definition(), as.as(), LazyValue.of(as.type()));
      default -> { }
    }

    pat.forEach(this::visitPattern);
  }

  default void visitParamDecl(Expr.@NotNull Param param) {
    visitVarDecl(param.sourcePos(), param.ref(), withTermType(param));
  }
  private void visitExpr(@NotNull WithPos<Expr> expr) { visitExpr(expr.sourcePos(), expr.data()); }
  default void visitExpr(@NotNull SourcePos pos, @NotNull Expr expr) {
    switch (expr) {
      case Expr.Ref ref -> visitVarRef(pos, ref.var(), withTermType(ref));
      case Expr.Lambda lam -> visitParamDecl(lam.param());
      case Expr.Pi pi -> visitParamDecl(pi.param());
      case Expr.Sigma sigma -> sigma.params().forEach(this::visitParamDecl);
      case Expr.Array array -> array.arrayBlock().forEach(
        left -> left.binds().forEach(bind -> visitVarDecl(bind.sourcePos(), bind.var(), noType)),
        _ -> { }
      );
      case Expr.Let let -> visitVarDecl(let.bind().sourcePos(), let.bind().bindName(), noType);
      case Expr.Do du -> du.binds().forEach(bind -> visitVarDecl(pos, bind.var(), noType));
      case Expr.Proj proj when proj.ix().isRight() && proj.resolvedVar() != null ->
        visitVarRef(proj.ix().getRightValue().sourcePos(), proj.resolvedVar(), lazyType(proj.resolvedVar()));
      // case Expr.New neu -> neu.fields().view().forEach((field) -> {
      //   // TODO: type for `field.bindings()`
      //   var acc1 = field.bindings().forEach((a, binding) -> visitVarDecl(a, binding.data(), binding.sourcePos(), noType()));
      //   var fieldRef = field.resolvedField().get();
      //   return fieldRef != null ? visitVarRef(acc1, fieldRef, field.name().sourcePos(), lazyType(fieldRef)) : acc1;
      // });
      // case Expr.Match match -> match.clauses().forEach(clause -> clause.patterns.forEach(ac,
      //   (a, p) -> visit(a, p.term())));
      default -> { }
    }

    expr.forEach(this::visitExpr);
  }

  default void visitTelescopic(@NotNull Decl telescopic) {
    telescopic.telescope.forEach(param -> param.forEach(this::visitExpr));
    if (telescopic.result != null) visitExpr(telescopic.result);
  }
  private @NotNull LazyValue<@Nullable Term> withTermType(@NotNull Expr.WithTerm term) {
    return LazyValue.of(term::coreType);
  }
}
