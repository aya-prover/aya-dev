// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.value.MutableValue;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.error.OperatorError;
import org.aya.concrete.stmt.*;
import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.ref.DefVar;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.NameProblem;
import org.aya.tyck.order.TyckOrder;
import org.aya.tyck.order.TyckUnit;
import org.aya.util.binop.OpDecl;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves expressions inside stmts, after {@link StmtShallowResolver}
 *
 * @author re-xyr, ice1000, kiva
 * @see StmtShallowResolver
 * @see ExprResolver
 */
public interface StmtResolver {
  static void resolveStmt(@NotNull SeqLike<@NotNull Stmt> stmt, @NotNull ResolveInfo info) {
    stmt.forEach(s -> resolveStmt(s, info));
  }

  /** @apiNote Note that this function MUTATES the stmt if it's a Decl. */
  static void resolveStmt(@NotNull Stmt stmt, @NotNull ResolveInfo info) {
    switch (stmt) {
      case Decl decl -> resolveDecl(decl, info);
      case Command.Module mod -> resolveStmt(mod.contents(), info);
      case Command cmd -> {}
      case Generalize variables -> {
        assert variables.ctx != null;
        var resolver = new ExprResolver(variables.ctx, ExprResolver.RESTRICTIVE);
        resolver.enterBody();
        variables.type = resolver.apply(variables.type);
        addReferences(info, new TyckOrder.Body(variables), resolver);
      }
    }
  }

  /** @apiNote Note that this function MUTATES the decl */
  private static void resolveDecl(@NotNull Decl predecl, @NotNull ResolveInfo info) {
    switch (predecl) {
      case TeleDecl.FnDecl decl -> {
        var resolver = resolveDeclSignature(decl, ExprResolver.LAX, info);
        resolver.enterBody();
        decl.body = decl.body.map(resolver, pats -> pats.map(resolver::apply));
        addReferences(info, new TyckOrder.Body(decl), resolver);
      }
      case TeleDecl.DataDecl decl -> {
        var resolver = resolveDeclSignature(decl, ExprResolver.LAX, info);
        resolver.enterBody();
        decl.body.forEach(ctor -> {
          var bodyResolver = resolver.member(decl, ExprResolver.Where.Head);
          var mCtx = MutableValue.create(resolver.ctx());
          ctor.patterns = ctor.patterns.map(pat -> pat.descent(pattern -> ExprResolver.resolve(pattern, mCtx)));
          resolveMemberSignature(ctor, bodyResolver, mCtx);
          ctor.clauses = bodyResolver.partial(mCtx.get(), ctor.clauses);
          var head = new TyckOrder.Head(ctor);
          addReferences(info, head, bodyResolver);
          addReferences(info, new TyckOrder.Body(ctor), SeqView.of(head));
          // No body no body but you!
        });
        addReferences(info, new TyckOrder.Body(decl), resolver.reference().view()
          .concat(decl.body.map(TyckOrder.Body::new)));
      }
      case ClassDecl decl -> {
        assert decl.ctx != null;
        var resolver = new ExprResolver(decl.ctx, ExprResolver.RESTRICTIVE);
        resolver.enterHead();
        decl.fields.forEach(field -> {
          var bodyResolver = resolver.member(decl, ExprResolver.Where.Head);
          var mCtx = MutableValue.create(resolver.ctx());
          resolveMemberSignature(field, bodyResolver, mCtx);
          addReferences(info, new TyckOrder.Head(field), bodyResolver.reference().view()
            .appended(new TyckOrder.Head(decl)));
          bodyResolver.enterBody();
          field.body = field.body.map(bodyResolver.enter(mCtx.get()));
          addReferences(info, new TyckOrder.Body(field), bodyResolver);
        });
        addReferences(info, new TyckOrder.Head(decl), resolver.reference().view()
          .concat(decl.fields.map(TyckOrder.Head::new)));
      }
      case TeleDecl.PrimDecl decl -> {
        resolveDeclSignature(decl, ExprResolver.RESTRICTIVE, info);
        addReferences(info, new TyckOrder.Body(decl), SeqView.empty());
      }
      // handled in DataDecl and StructDecl
      case TeleDecl.DataCtor ctor -> {}
      case TeleDecl.ClassMember field -> {}
    }
  }
  private static <T extends TeleDecl<?> & TyckUnit>
  void resolveMemberSignature(T ctor, ExprResolver bodyResolver, MutableValue<@NotNull Context> mCtx) {
    ctor.modifyTelescope(t -> t.map(param -> bodyResolver.resolve(param, mCtx)));
    // If changed to method reference, `bodyResolver.enter(mCtx.get())` will be evaluated eagerly
    //  so please don't
    ctor.modifyResult(t -> bodyResolver.enter(mCtx.get()).apply(t));
  }

  private static void addReferences(@NotNull ResolveInfo info, TyckOrder decl, SeqView<TyckOrder> refs) {
    info.depGraph().sucMut(decl).appendAll(refs
      .filter(unit -> unit.unit().needTyck(info.thisModule().moduleName())));
    if (decl instanceof TyckOrder.Body) info.depGraph().sucMut(decl)
      .append(new TyckOrder.Head(decl.unit()));
  }

  private static void addReferences(@NotNull ResolveInfo info, TyckOrder decl, ExprResolver resolver) {
    addReferences(info, decl, resolver.reference().view());
  }

  private static @NotNull ExprResolver resolveDeclSignature(
    @NotNull TeleDecl.TopLevel<?> decl,
    ExprResolver.@NotNull Options options,
    @NotNull ResolveInfo info
  ) {
    assert decl.ctx != null;
    var resolver = new ExprResolver(decl.ctx, options);
    resolver.enterHead();
    var mCtx = MutableValue.create(decl.ctx);
    var telescope = decl.telescope.map(param -> resolver.resolve(param, mCtx));
    var newResolver = resolver.enter(mCtx.get());
    decl.modifyResult(newResolver);
    decl.telescope = telescope.prependedAll(newResolver.allowedGeneralizes().valuesView());
    addReferences(info, new TyckOrder.Head(decl), resolver);
    return newResolver;
  }

  static void visitBind(@NotNull DefVar<?, ?> selfDef, @NotNull BindBlock bind, @NotNull ResolveInfo info) {
    var opSet = info.opSet();
    var self = selfDef.opDecl;
    if (self == null && bind != BindBlock.EMPTY) {
      opSet.reporter.report(new OperatorError.BadBindBlock(selfDef.concrete.sourcePos(), selfDef.name()));
      throw new Context.ResolvingInterruptedException();
    }
    bind(bind, opSet, self);
  }

  private static void bind(@NotNull BindBlock bindBlock, AyaBinOpSet opSet, OpDecl self) {
    if (bindBlock == BindBlock.EMPTY) return;
    var ctx = bindBlock.context().get();
    assert ctx != null : "no shallow resolver?";
    bindBlock.resolvedLoosers().set(bindBlock.loosers().map(looser -> bind(self, opSet, ctx, OpDecl.BindPred.Looser, looser)));
    bindBlock.resolvedTighters().set(bindBlock.tighters().map(tighter -> bind(self, opSet, ctx, OpDecl.BindPred.Tighter, tighter)));
  }

  private static @NotNull DefVar<?, ?> bind(
    @NotNull OpDecl self, @NotNull AyaBinOpSet opSet, @NotNull Context ctx,
    @NotNull OpDecl.BindPred pred, @NotNull QualifiedID id
  ) throws Context.ResolvingInterruptedException {
    if (ctx.get(id) instanceof DefVar<?, ?> defVar) {
      var opDecl = defVar.resolveOpDecl(ctx.moduleName());
      if (opDecl != null) {
        opSet.bind(self, pred, opDecl, id.sourcePos());
        return defVar;
      }
    }

    // make compiler happy 😥
    throw resolvingInterrupt(opSet.reporter, new NameProblem.OperatorNameNotFound(id.sourcePos(), id.join()));
  }

  static void resolveBind(@NotNull SeqLike<@NotNull Stmt> contents, @NotNull ResolveInfo info) {
    contents.forEach(s -> resolveBind(s, info));
    info.opRename().forEach((k, v) -> {
      if (v.component2() == BindBlock.EMPTY) return;
      bind(v.component2(), info.opSet(), v.component1());
    });
  }

  static void resolveBind(@NotNull Stmt stmt, @NotNull ResolveInfo info) {
    switch (stmt) {
      case Command.Module mod -> resolveBind(mod.contents(), info);
      case ClassDecl decl -> {
        decl.fields.forEach(field -> resolveBind(field, info));
        visitBind(decl.ref, decl.bindBlock(), info);
      }
      case TeleDecl.DataDecl decl -> {
        decl.body.forEach(ctor -> resolveBind(ctor, info));
        visitBind(decl.ref, decl.bindBlock(), info);
      }
      case TeleDecl.DataCtor ctor -> visitBind(ctor.ref, ctor.bindBlock(), info);
      case TeleDecl.ClassMember field -> visitBind(field.ref, field.bindBlock(), info);
      case TeleDecl.FnDecl decl -> visitBind(decl.ref, decl.bindBlock(), info);
      case TeleDecl.PrimDecl decl -> {}
      case Command cmd -> {}
      case Generalize generalize -> {}
    }
  }

  @Contract("_, _ -> fail")
  static Context.ResolvingInterruptedException resolvingInterrupt(Reporter reporter, Problem problem) {
    reporter.report(problem);
    throw new Context.ResolvingInterruptedException();
  }
}
