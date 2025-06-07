// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.LazyValue;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.*;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface StmtVisitor extends Consumer<Stmt> {
  record Type(@Nullable Expr userType, @NotNull LazyValue<@Nullable Term> lazyType) implements AyaDocile {
    public static final @NotNull Doc noTypeDoc = Doc.plain("<error>");
    public static final @NotNull Type noType = new Type(null, LazyValue.ofValue(null));

    public Type(@NotNull LazyValue<@Nullable Term> lazyType) {
      this(null, lazyType);
    }

    public Type(@NotNull Expr userType) {
      this(userType, LazyValue.ofValue(null));
    }

    public @Nullable AyaDocile toDocile() {
      AyaDocile docile = lazyType.get();
      return docile == null ? userType : docile;
    }

    @Override
    public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      var doc = toDocile();
      if (doc == null) return noTypeDoc;
      return doc.toDoc(options);
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

  /// @implNote the impl should be able to handle {@link SourcePos#NONE} and {@link LocalVar#IGNORED}.
  default void visitVarDecl(
    @NotNull SourcePos pos, @NotNull AnyVar var,
    @NotNull Type type
  ) { visitVar(pos, var, type); }

  @ApiStatus.NonExtendable
  default void visitLocalVarDecl(@NotNull LocalVar var, @NotNull Type type) {
    visitVarDecl(var.definition(), var, type);
  }

  @ApiStatus.NonExtendable
  default void visitParam(@NotNull Expr.Param param) {
    visitExpr(param.typeExpr());
    visitParamDecl(param);
  }

  @ApiStatus.NonExtendable
  default void visitParamDecl(Expr.@NotNull Param param) {
    visitLocalVarDecl(param.ref(), fromParam(param));
  }

  @ApiStatus.NonExtendable
  default void visitGeneralizedVarDecl(@NotNull GeneralizedVar v) {
    visitVarDecl(v.sourcePos, v, new Type(v.owner.type.data()));
  }

  @ApiStatus.NonExtendable
  default void visitDoBind(@NotNull Expr.DoBind bind) {
    visitExpr(bind.expr());
    visitLocalVarDecl(bind.var(), Type.noType);
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

  default void accept(@NotNull Stmt stmt) {
    switch (stmt) {
      case Decl decl -> visitDecl(decl);
      case Command command -> {
        switch (command) {
          case Command.Module module -> {
            visitModuleDecl(module.sourcePos(), ModuleName.of(module.name()));      // TODO: what about nested module, also 1-length name?
            module.contents().forEach(this);
          }
          case Command.Open o when o.fromSugar() -> { }
          case Command.Open o -> {
            visitModuleRef(o.sourcePos(), o.path());
            // TODO: what about the symbols that introduced by renaming
            // https://github.com/aya-prover/aya-dev/issues/721
            o.useHide().list().forEach(v -> visit(v.asBind()));
          }
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
        }
      }
      case Generalize g -> {
        visitExpr(g.type);
        g.variables.forEach(this::visitGeneralizedVarDecl);
      }
    }
  }

  default void visitDecl(@NotNull Decl decl) {
    visitVarDecl(decl.nameSourcePos(), decl.ref(), lazyType(decl.ref()));
    visit(decl.bindBlock());

    if (decl instanceof TeleDecl tele) visitTelescopic(tele);
    switch (decl) {
      case DataDecl data -> visitDataDecl(data);
      case ClassDecl clazz -> visitClassDecl(clazz);
      case FnDecl fn -> visitFnDecl(fn);
      case DataCon con -> visitDataCon(con);
      case PrimDecl prim -> visitPrimDecl(prim);
      case ClassMember member -> visitClassMember(member);
    }
  }

  default void visitDataDecl(@NotNull DataDecl decl) {
    decl.body.forEach(this::accept);
  }

  default void visitClassDecl(@NotNull ClassDecl decl) {
    decl.members.forEach(this);
  }

  default void visitFnDecl(@NotNull FnDecl decl) {
    if (decl.body instanceof FnBody.BlockBody block) {
      if (block.elims() != null) block.elims().forEachWith(block.rawElims(), (var, name) ->
        visitVarRef(name.sourcePos(), var, Type.noType));
    }

    decl.body.forEach(this::visitExpr, this::visitClause);
  }

  default void visitDataCon(@NotNull DataCon decl) {
    decl.patterns.forEach(cl -> visitPattern(cl.term()));
  }

  default void visitPrimDecl(@NotNull PrimDecl decl) { }

  default void visitClassMember(@NotNull ClassMember decl) { }

  default void visitDoBinds(@NotNull SeqView<Expr.DoBind> binds) {
    binds.forEach(this::visitDoBind);
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

        con.forEach(this::visitPattern);
      }
      case Pattern.Bind bind -> visitLocalVarDecl(bind.bind(), new Type(LazyValue.of(bind.type())));
      case Pattern.As as -> {
        // visit before as var decl
        as.forEach(this::visitPattern);
        visitLocalVarDecl(as.as(), new Type(LazyValue.of(as.type())));
      }

      default -> pat.forEach(this::visitPattern);
    }
  }

  default void visitMatch(@NotNull Expr.Match match) {
    var discriminant = match.discriminant();
    discriminant.forEach(it -> visitExpr(it.discr()));
    discriminant.view()
      .mapNotNull(Expr.Match.Discriminant::asBinding)
      .forEach(it -> visitLocalVarDecl(it, Type.noType));

    var returns = match.returns();
    if (returns != null) visitExpr(returns);

    match.clauses().forEach(this::visitClause);
  }

  // scope introducer
  default void visitLetBind(@NotNull Expr.LetBind bind) {
    var result = bind.result();
    // visit let bind
    visitTelescope(bind.telescope().view(), result.data() instanceof Expr.Hole ? null : result);
    visitExpr(bind.definedAs());
  }

  default void visitLetBody(@NotNull Expr.Let let) {
    var bind = let.bind();
    var body = let.body();

    var result = bind.result();
    // it is possible that it has telescope without return type
    var hasType = bind.telescope().isNotEmpty() || !(result.data() instanceof Expr.Hole);
    Type type;
    if (!hasType) {
      type = Type.noType;
    } else {
      // dummy pos, as we don't really need it.
      var piType = Expr.buildPi(SourcePos.NONE, bind.telescope().view(), result).data();
      type = new Type(piType);
    }

    visitLocalVarDecl(bind.bindName(), type);
    visitExpr(body);
  }

  private void visitExpr(@NotNull WithPos<Expr> expr) { visitExpr(expr.sourcePos(), expr.data()); }
  default void visitExpr(@NotNull SourcePos pos, @NotNull Expr expr) {
    switch (expr) {
      case Expr.Unresolved unresolved -> visitUnresolvedRef(unresolved.name());
      case Expr.Ref ref -> visitVarRef(pos, ref.var(), withTermType(ref));
      case Expr.Lambda lam -> {
        visitLocalVarDecl(lam.ref(), Type.noType);
        visitExpr(lam.body());
      }
      case Expr.ClauseLam lam -> visitClause(lam.clause());
      case Expr.DepType depType -> {
        visitParam(depType.param());
        visitExpr(depType.last());
      }
      case Expr.Array array when array.arrayBlock().isLeft() -> {
        var compBlock = array.arrayBlock().getLeftValue();
        var gen = compBlock.generator();
        visitDoBinds(compBlock.binds().view()
          .appended(new Expr.DoBind(gen)));
      }
      case Expr.Let let -> {
        visitLetBind(let.bind());
        visitLetBody(let);
      }
      case Expr.LetOpen letOpen -> {
        var module = letOpen.componentName();
        visitModuleRef(module.sourcePos(), module.data());
        visitExpr(letOpen.body());
      }
      case Expr.Do du -> visitDoBinds(du.binds().view());
      case Expr.Proj proj when proj.ix().isRight() && proj.resolvedVar() != null -> {
        visitVarRef(proj.ix().getRightValue().sourcePos(), proj.resolvedVar(), lazyType(proj.resolvedVar()));
        visitExpr(proj.tup());
      }
      case Expr.Match match -> visitMatch(match);
      default -> expr.forEach(this::visitExpr);
    }
  }

  default void visitTelescopic(@NotNull TeleDecl telescopic) {
    visitTelescope(telescopic.telescope.view(), telescopic.result);
  }

  /// Visit a telescope,
  default void visitTelescope(@NotNull SeqView<Expr.Param> params, @Nullable WithPos<Expr> result) {
    params.forEach(this::visitParam);
    if (result != null) visitExpr(result);
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
