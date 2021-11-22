// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.PhysicalModuleContext;
import org.aya.concrete.resolve.error.ModNotFoundError;
import org.aya.concrete.resolve.error.UnknownOperatorError;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.stmt.BindBlock;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.StructDef;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.function.Function;

/**
 * The .ayac file representation.
 *
 * @author kiva
 */
public record CompiledAya(
  @NotNull ImmutableSeq<ImmutableSeq<String>> imports,
  @NotNull ImmutableSeq<SerDef.QName> exports,
  @NotNull ImmutableSeq<SerDef> serDefs,
  @NotNull ImmutableSeq<SerDef.SerOp> serOps
) implements Serializable {
  public static @NotNull CompiledAya from(
    @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<Def> defs,
    @NotNull Serializer.State state
  ) {
    if (!(resolveInfo.thisModule() instanceof PhysicalModuleContext ctx)) {
      // TODO[kiva]: how to reach here?
      throw new UnsupportedOperationException();
    }

    var serialization = new Serialization(state, DynamicSeq.create(), DynamicSeq.create());
    serialization.ser(defs);

    var modName = ctx.moduleName();
    var exports = ctx.exports.view().map((k, vs) -> {
      var qnameMod = modName.appendedAll(k);
      return vs.view().map((n, v) -> new SerDef.QName(qnameMod, n));
    }).flatMap(Function.identity()).toImmutableSeq();

    var imports = resolveInfo.imports().toImmutableSeq().map(i -> i.thisModule().moduleName());
    return new CompiledAya(imports, exports, serialization.serDefs.toImmutableSeq(), serialization.serOps.toImmutableSeq());
  }

  private record Serialization(
    @NotNull Serializer.State state,
    @NotNull DynamicSeq<SerDef> serDefs,
    @NotNull DynamicSeq<SerDef.SerOp> serOps
  ) {
    private void ser(@NotNull ImmutableSeq<Def> defs) {
      defs.forEach(this::serDef);
    }

    private void serDef(@NotNull Def def) {
      var serDef = def.accept(new Serializer(state), Unit.unit());
      serDefs.append(serDef);
      serOp(serDef, def);
      switch (serDef) {
        case SerDef.Data data -> data.bodies().view().zip(((DataDef) def).body).forEach(tup ->
          serOp(tup._1, tup._2));
        case SerDef.Struct struct -> struct.fields().view().zip(((StructDef) def).fields).forEach(tup ->
          serOp(tup._1, tup._2));
        default -> {}
      }
    }

    private void serOp(@NotNull SerDef serDef, @NotNull Def def) {
      var concrete = def.ref().concrete;
      var opInfo = concrete.opInfo;
      if (opInfo != null) serOps.append(new SerDef.SerOp(
        nameOf(serDef), opInfo.assoc(), opInfo.argc(),
        serBind(concrete.bindBlock)));
    }

    private @NotNull SerDef.SerBind serBind(@NotNull BindBlock bindBlock) {
      if (bindBlock == BindBlock.EMPTY) return SerDef.SerBind.EMPTY;
      var loosers = bindBlock.resolvedLoosers().value.map(state::def);
      var tighters = bindBlock.resolvedTighters().value.map(state::def);
      return new SerDef.SerBind(loosers, tighters);
    }
  }

  private static SerDef.QName nameOf(@NotNull SerDef def) {
    return switch (def) {
      case SerDef.Fn fn -> fn.name();
      case SerDef.Struct struct -> struct.name();
      case SerDef.Field field -> field.self();
      case SerDef.Data data -> data.name();
      case SerDef.Ctor ctor -> ctor.self();
      case SerDef.Prim prim -> new SerDef.QName(ImmutableSeq.empty(), prim.name().name());
    };
  }

  public @NotNull ResolveInfo toResolveInfo(@NotNull CachedModuleLoader loader, @NotNull PhysicalModuleContext context, @NotNull SerTerm.DeState state) {
    var resolveInfo = new ResolveInfo(context, ImmutableSeq.empty(), new AyaBinOpSet(context.reporter()));
    shallowResolve(loader, resolveInfo);
    serDefs.forEach(serDef -> de(context, serDef, state));
    deOp(state, resolveInfo.opSet());
    return resolveInfo;
  }

  /** like {@link org.aya.concrete.resolve.visitor.StmtShallowResolver} but only resolve import */
  private void shallowResolve(@NotNull CachedModuleLoader loader, @NotNull ResolveInfo thisResolve) {
    for (var modName : imports) {
      var success = loader.load(modName);
      if (success == null) thisResolve.thisModule().reportAndThrow(new ModNotFoundError(modName, SourcePos.SER));
      thisResolve.imports().append(success);
      var mod = (PhysicalModuleContext) success.thisModule(); // this cast should never fail
      thisResolve.thisModule().importModules(modName, Stmt.Accessibility.Private, mod.exports, SourcePos.SER);
      thisResolve.opSet().operators.putAll(success.opSet().operators);
    }
  }

  /** like {@link org.aya.concrete.resolve.visitor.StmtResolver} but only resolve operator */
  private void deOp(@NotNull SerTerm.DeState state, @NotNull AyaBinOpSet opSet) {
    serOps.forEach(serOp -> {
      var defVar = state.def(serOp.name());
      var opInfo = new OpDecl.OpInfo(serOp.name().name(), serOp.assoc(), serOp.argc());
      var opDecl = new SerDef.SerOpDecl(opInfo);
      opSet.operators.put(defVar, opDecl);
    });
    serOps.view().forEach(serOp -> {
      var defVar = state.def(serOp.name());
      var opDecl = opSet.operators.get(defVar);
      var bind = serOp.bind();
      opSet.ensureHasElem(opDecl);
      bind.loosers().forEach(looser -> {
        var target = resolveOp(opSet, looser);
        opSet.bind(opDecl, OpDecl.BindPred.Looser, target, SourcePos.SER);
      });
      bind.tighters().forEach(tighter -> {
        var target = resolveOp(opSet, tighter);
        opSet.bind(opDecl, OpDecl.BindPred.Looser, target, SourcePos.SER);
      });
    });
  }

  private @NotNull OpDecl resolveOp(@NotNull AyaBinOpSet opSet, @NotNull SerDef.QName name) {
    var iter = opSet.operators.iterator();
    while (iter.hasNext()) {
      var next = iter.next();
      var defVar = next._1;
      if (defVar.module.equals(name.mod()) && defVar.name().equals(name.name())) return next._2;
    }
    opSet.reporter.report(new UnknownOperatorError(SourcePos.SER, name.name()));
    throw new Context.ResolvingInterruptedException();
  }

  private void de(@NotNull PhysicalModuleContext context, @NotNull SerDef serDef, @NotNull SerTerm.DeState state) {
    var mod = context.moduleName();
    var drop = mod.size();
    var def = serDef.de(state);
    assert def.ref().core != null;
    switch (serDef) {
      case SerDef.Fn fn -> {
        if (isExported(fn.name())) {
          export(context, drop, fn.name(), def.ref());
          export(context, fn.name().name(), def.ref());
        }
      }
      case SerDef.Data data -> {
        var innerCtx = context.derive(data.name().name());
        if (isExported(data.name())) export(context, data.name().name(), def.ref());
        data.bodies().view().zip(((DataDef) def).body).forEach(tup -> {
          if (isExported(tup._1.self())) export(context, drop, tup._1.self(), tup._2.ref);
          export(innerCtx, tup._1.self().name(), tup._2.ref);
        });
        context.importModules(innerCtx.moduleName().drop(drop), Stmt.Accessibility.Public, innerCtx.exports, SourcePos.SER);
      }
      case SerDef.Struct struct -> {
        var innerCtx = context.derive(struct.name().name());
        if (isExported(struct.name())) export(context, struct.name().name(), def.ref());
        struct.fields().view().zip(((StructDef) def).fields).forEach(tup -> {
          if (isExported(tup._1.self())) export(context, drop, tup._1.self(), tup._2.ref);
          export(innerCtx, tup._1.self().name(), tup._2.ref);
        });
        context.importModules(innerCtx.moduleName().drop(drop), Stmt.Accessibility.Public, innerCtx.exports, SourcePos.SER);
      }
      case SerDef.Prim prim -> export(context, mod.drop(drop), prim.name().id, def.ref());
      default -> {}
    }
  }

  private void export(@NotNull PhysicalModuleContext context, int dropMod, @NotNull SerDef.QName qname, DefVar<?, ?> ref) {
    export(context, qname.mod().drop(dropMod), qname.name(), ref);
  }

  private void export(@NotNull PhysicalModuleContext context, @NotNull String name, @NotNull Var var) {
    export(context, ImmutableSeq.empty(), name, var);
  }

  private void export(@NotNull PhysicalModuleContext context, @NotNull ImmutableSeq<String> mod, @NotNull String name, @NotNull Var var) {
    context.exports.getOrPut(ImmutableSeq.empty(), MutableMap::create).put(name, var);
    context.exports.getOrPut(mod, MutableMap::create).put(name, var);
  }

  private boolean isExported(@NotNull SerDef.QName qname) {
    return exports.contains(qname);
  }
}
