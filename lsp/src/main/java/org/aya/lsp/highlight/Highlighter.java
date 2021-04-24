// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.highlight;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.concrete.Generalize;
import org.aya.concrete.Stmt;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.TermConsumer;
import org.aya.generic.Matching;
import org.aya.lsp.LspRange;
import org.eclipse.lsp4j.Range;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

public class Highlighter implements
  Def.Visitor<@NotNull Buffer<Symbol>, Unit>,
  Stmt.Visitor<@NotNull Buffer<Symbol>, Unit>,
  TermConsumer<@NotNull Buffer<Symbol>>,
  Pat.Visitor<@NotNull Buffer<Symbol>, Unit> {

  private static final Highlighter INSTANCE = new Highlighter();

  public static void buildTycked(@NotNull Buffer<Symbol> buffer,
                                 @NotNull ImmutableSeq<Def> defs) {
    defs.forEach(def -> def.accept(INSTANCE, buffer));
  }

  public static void buildResolved(@NotNull Buffer<Symbol> buffer,
                                   @NotNull ImmutableSeq<Stmt> stmts) {
    stmts.forEach(stmt -> stmt.accept(INSTANCE, buffer));
  }

  private @NotNull Range posOf(@NotNull Def def) {
    return LspRange.from(def.ref().concrete.sourcePos);
  }

  private @NotNull Range posOf(@NotNull Stmt stmt) {
    return LspRange.from(stmt.sourcePos());
  }

  // region def, data, struct, prim, levels

  private void visitClauses(@NotNull ImmutableSeq<Matching<Pat, Term>> ms, @NotNull Buffer<Symbol> buffer) {
    ms.forEach(m -> {
      m.patterns().forEach(p -> p.accept(this, buffer));
      m.body().accept(this, buffer);
    });
  }

  private void visitTele(@NotNull ImmutableSeq<Term.Param> telescope, @NotNull Buffer<Symbol> buffer) {
    telescope.forEach(p -> p.type().accept(this, buffer));
  }

  @Override public Unit visitFn(@NotNull FnDef def, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(posOf(def), Symbol.Kind.FnDef));
    visitTele(def.telescope(), buffer);
    def.result().accept(this, buffer);
    def.body().forEach(t -> t.accept(this, buffer), ms -> visitClauses(ms, buffer));
    return Unit.unit();
  }

  @Override public Unit visitData(@NotNull DataDef def, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(posOf(def), Symbol.Kind.DataDef));
    visitTele(def.telescope(), buffer);
    def.result().accept(this, buffer);
    def.body().forEach(ctor -> ctor.accept(this, buffer));
    return Unit.unit();
  }

  @Override public Unit visitCtor(@NotNull DataDef.Ctor def, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(posOf(def), Symbol.Kind.ConDef));
    visitTele(def.conTele(), buffer);
    def.pats().forEach(p -> p.accept(this, buffer));
    visitClauses(def.clauses(), buffer);
    return Unit.unit();
  }

  @Override public Unit visitStruct(@NotNull StructDef def, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(posOf(def), Symbol.Kind.StructDef));
    visitTele(def.telescope(), buffer);
    def.result().accept(this, buffer);
    def.fields().forEach(f -> f.accept(this, buffer));
    return Unit.unit();
  }

  @Override public Unit visitField(@NotNull StructDef.Field def, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(posOf(def), Symbol.Kind.FieldDef));
    visitTele(def.fieldTele(), buffer);
    def.result().accept(this, buffer);
    def.body().forEach(t -> t.accept(this, buffer));
    visitClauses(def.clauses(), buffer);
    return Unit.unit();
  }

  @Override public Unit visitPrim(@NotNull PrimDef def, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(posOf(def), Symbol.Kind.PrimDef));
    visitTele(def.telescope(), buffer);
    def.result().accept(this, buffer);
    return Unit.unit();
  }

  @Override public Unit visitLevels(Generalize.@NotNull Levels levels, @NotNull Buffer<Symbol> buffer) {
    for (var level : levels.levels()) buffer.append(new Symbol(LspRange.from(level._1), Symbol.Kind.Param));
    return Unit.unit();
  }

  // endregion

  // region term

  private void visitCall(@NotNull DefVar<?, ?> ref, @NotNull SourcePos headPos, @NotNull Buffer<Symbol> buffer) {
    if (ref.core instanceof FnDef) buffer.append(new Symbol(LspRange.from(headPos), Symbol.Kind.FnCall));
    else if (ref.core instanceof PrimDef) buffer.append(new Symbol(LspRange.from(headPos), Symbol.Kind.PrimCall));
    else if (ref.core instanceof DataDef) buffer.append(new Symbol(LspRange.from(headPos), Symbol.Kind.DataCall));
    else if (ref.core instanceof DataDef.Ctor) buffer.append(new Symbol(LspRange.from(headPos), Symbol.Kind.ConCall));
    else if (ref.core instanceof StructDef) buffer.append(new Symbol(LspRange.from(headPos), Symbol.Kind.StructCall));
    else if (ref.core instanceof StructDef.Field)
      buffer.append(new Symbol(LspRange.from(headPos), Symbol.Kind.FieldCall));
  }

  @Override public Unit visitFnCall(CallTerm.@NotNull Fn fnCall, @NotNull Buffer<Symbol> buffer) {
    visitCall(fnCall.ref(), fnCall.sourcePos(), buffer);
    visitArgs(buffer, fnCall.args());
    return Unit.unit();
  }

  @Override public Unit visitPrimCall(CallTerm.@NotNull Prim prim, @NotNull Buffer<Symbol> buffer) {
    visitCall(prim.ref(), prim.sourcePos(), buffer);
    visitArgs(buffer, prim.args());
    return Unit.unit();
  }

  @Override public Unit visitDataCall(CallTerm.@NotNull Data dataCall, @NotNull Buffer<Symbol> buffer) {
    visitCall(dataCall.ref(), dataCall.sourcePos(), buffer);
    visitArgs(buffer, dataCall.args());
    return Unit.unit();
  }

  @Override public Unit visitConCall(CallTerm.@NotNull Con conCall, @NotNull Buffer<Symbol> buffer) {
    visitCall(conCall.ref(), conCall.head().sourcePos(), buffer);
    visitArgs(buffer, conCall.args());
    return Unit.unit();
  }

  @Override public Unit visitStructCall(CallTerm.@NotNull Struct structCall, @NotNull Buffer<Symbol> buffer) {
    visitCall(structCall.ref(), structCall.sourcePos(), buffer);
    visitArgs(buffer, structCall.args());
    return Unit.unit();
  }

  @Override public Unit visitAccess(CallTerm.@NotNull Access term, @NotNull Buffer<Symbol> buffer) {
    visitCall(term.ref(), term.sourcePos(), buffer);
    term.of().accept(this, buffer);
    return Unit.unit();
  }

  // endregion

  // region pattern
  @Override public Unit visitBind(Pat.@NotNull Bind bind, @NotNull Buffer<Symbol> buffer) {
    return Unit.unit();
  }

  @Override public Unit visitTuple(Pat.@NotNull Tuple tuple, @NotNull Buffer<Symbol> buffer) {
    return Unit.unit();
  }

  @Override public Unit visitCtor(Pat.@NotNull Ctor ctor, @NotNull Buffer<Symbol> buffer) {
    return Unit.unit();
  }

  @Override public Unit visitAbsurd(Pat.@NotNull Absurd absurd, @NotNull Buffer<Symbol> buffer) {
    return Unit.unit();
  }

  @Override public Unit visitPrim(Pat.@NotNull Prim prim, @NotNull Buffer<Symbol> buffer) {
    return Unit.unit();
  }
  // endregion

  // region import, open, module

  @Override public Unit visitImport(Stmt.@NotNull ImportStmt cmd, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(posOf(cmd), Symbol.Kind.ModuleDef));
    return Unit.unit();
  }

  @Override public Unit visitOpen(Stmt.@NotNull OpenStmt cmd, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(posOf(cmd), Symbol.Kind.ModuleDef));
    return Unit.unit();
  }

  @Override public Unit visitModule(Stmt.@NotNull ModuleStmt mod, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(posOf(mod), Symbol.Kind.ModuleDef));
    return Unit.unit();
  }

  @Override public Unit visitBind(Stmt.@NotNull BindStmt bind, @NotNull Buffer<Symbol> buffer) {
    buffer.append(new Symbol(LspRange.from(bind.op().sourcePos()), Symbol.Kind.Operator));
    buffer.append(new Symbol(LspRange.from(bind.target().sourcePos()), Symbol.Kind.Operator));
    return Unit.unit();
  }

  @Override public Unit visitData(@NotNull Decl.DataDecl decl, @NotNull Buffer<Symbol> buffer) {
    // [kiva]: use Def.Visitor's version
    return Unit.unit();
  }

  @Override public Unit visitStruct(@NotNull Decl.StructDecl decl, @NotNull Buffer<Symbol> buffer) {
    // [kiva]: use Def.Visitor's version
    return Unit.unit();
  }

  @Override public Unit visitFn(@NotNull Decl.FnDecl decl, @NotNull Buffer<Symbol> buffer) {
    // [kiva]: use Def.Visitor's version
    return Unit.unit();
  }

  @Override public Unit visitPrim(@NotNull Decl.PrimDecl decl, @NotNull Buffer<Symbol> buffer) {
    // [kiva]: use Def.Visitor's version
    return Unit.unit();
  }

  // endregion
}
