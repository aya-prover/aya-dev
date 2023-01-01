// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple;
import kala.tuple.Tuple3;
import org.aya.concrete.stmt.BindBlock;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.UseHide;
import org.aya.core.def.DataDef;
import org.aya.core.def.GenericDef;
import org.aya.core.def.StructDef;
import org.aya.core.repr.AyaShape;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleExport;
import org.aya.resolve.context.PhysicalModuleContext;
import org.aya.resolve.error.NameProblem;
import org.aya.resolve.module.ModuleLoader;
import org.aya.resolve.visitor.StmtResolver;
import org.aya.resolve.visitor.StmtShallowResolver;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
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
  @NotNull ImmutableMap<ImmutableSeq<String>, SerUseHide> reExports,
  @NotNull ImmutableSeq<SerDef> serDefs,
  @NotNull ImmutableSeq<SerDef.SerOp> serOps,
  @NotNull ImmutableMap<SerDef.QName, SerDef.SerRenamedOp> opRename
) implements Serializable {
  /** @see org.aya.concrete.stmt.UseHide */
  record SerUseHide(
    boolean isUsing,
    @NotNull ImmutableSeq<String> names,
    @NotNull ImmutableSeq<UseHide.Rename> renames
  ) implements Serializable {
    public static @NotNull SerUseHide from(@NotNull UseHide useHide) {
      return new SerUseHide(
        useHide.strategy() == UseHide.Strategy.Using,
        useHide.list().map(UseHide.Name::id),
        useHide.renaming().map(WithPos::data)
      );
    }
  }

  public static @NotNull CompiledAya from(
    @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<GenericDef> defs,
    @NotNull Serializer.State state
  ) {
    if (!(resolveInfo.thisModule() instanceof PhysicalModuleContext ctx)) {
      // TODO[kiva]: how to reach here?
      throw new UnsupportedOperationException();
    }

    var serialization = new Serialization(state, resolveInfo, MutableList.create(), MutableList.create());
    serialization.ser(defs);

    var modName = ctx.moduleName();
    var exports = ctx.exports.view().map((k, vs) -> {
      var qnameMod = modName.appendedAll(k);
      return vs.exports().view().map((n, v) -> new SerDef.QName(qnameMod, n));
    }).flatMap(Function.identity()).toImmutableSeq();

    var imports = resolveInfo.imports().valuesView().map(i -> i.thisModule().moduleName()).toImmutableSeq();
    return new CompiledAya(imports, exports,
      resolveInfo.reExports().view()
        .map((k, v) -> Tuple.of(k, SerUseHide.from(v)))
        .toImmutableMap(),
      serialization.serDefs.toImmutableSeq(),
      serialization.serOps.toImmutableSeq(),
      resolveInfo.opRename().view().map((k, v) -> {
          var name = state.def(k);
          var info = v.component1().opInfo();
          var renamed = new SerDef.SerRenamedOp(info.name(), info.assoc(), serialization.serBind(v.component2()));
          return Tuple.of(v.component3(), name, renamed);
        })
        .filter(Tuple3::head) // should not serialize publicly renamed ops from upstreams
        .map(Tuple3::tail)
        .toImmutableMap()
    );
  }

  private record Serialization(
    @NotNull Serializer.State state,
    @NotNull ResolveInfo resolveInfo,
    @NotNull MutableList<SerDef> serDefs,
    @NotNull MutableList<SerDef.SerOp> serOps
  ) {
    private void ser(@NotNull ImmutableSeq<GenericDef> defs) {
      defs.forEach(this::serDef);
    }

    private void serDef(@NotNull GenericDef def) {
      var serDef = new Serializer(state).serialize(def);
      serDefs.append(serDef);
      serOp(serDef, def);
      switch (serDef) {
        case SerDef.Data data -> data.bodies().forEachWith(((DataDef) def).body, this::serOp);
        case SerDef.Struct struct -> struct.fields().forEachWith(((StructDef) def).fields, this::serOp);
        default -> {}
      }
    }

    private void serOp(@NotNull SerDef serDef, @NotNull GenericDef def) {
      var concrete = def.ref().concrete;
      var opInfo = concrete.opInfo();
      if (opInfo != null) serOps.append(new SerDef.SerOp(
        nameOf(serDef), opInfo.assoc(), serBind(concrete.bindBlock())));
    }

    private @NotNull SerDef.SerBind serBind(@NotNull BindBlock bindBlock) {
      if (bindBlock == BindBlock.EMPTY) return SerDef.SerBind.EMPTY;
      var loosers = bindBlock.resolvedLoosers().get().map(state::def);
      var tighters = bindBlock.resolvedTighters().get().map(state::def);
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

  public @NotNull ResolveInfo toResolveInfo(@NotNull ModuleLoader loader, @NotNull PhysicalModuleContext context, @NotNull SerTerm.DeState state) {
    var resolveInfo = new ResolveInfo(state.primFactory(), context, ImmutableSeq.empty());
    shallowResolve(loader, resolveInfo);
    serDefs.forEach(serDef -> de(resolveInfo.shapeFactory(), context, serDef, state));
    deOp(state, resolveInfo);
    return resolveInfo;
  }

  /** like {@link StmtShallowResolver} but only resolve import */
  private void shallowResolve(@NotNull ModuleLoader loader, @NotNull ResolveInfo thisResolve) {
    for (var modName : imports) {
      var success = loader.load(modName);
      if (success == null)
        thisResolve.thisModule().reportAndThrow(new NameProblem.ModNotFoundError(modName, SourcePos.SER));
      thisResolve.imports().put(success.thisModule().moduleName(), success);
      var mod = (PhysicalModuleContext) success.thisModule(); // this cast should never fail
      thisResolve.thisModule().importModules(modName, Stmt.Accessibility.Private, mod.exports, SourcePos.SER);
      reExports.getOption(modName).forEach(useHide -> thisResolve.thisModule().openModule(modName,
        Stmt.Accessibility.Public,
        useHide.names().map(x -> new WithPos<>(SourcePos.SER, x)),
        useHide.renames().map(x -> new WithPos<>(SourcePos.SER, x)),
        SourcePos.SER, useHide.isUsing()));
      var acc = this.reExports.containsKey(modName)
        ? Stmt.Accessibility.Public
        : Stmt.Accessibility.Private;
      thisResolve.open(success, SourcePos.SER, acc);
    }
  }

  /** like {@link StmtResolver} but only resolve operator */
  private void deOp(@NotNull SerTerm.DeState state, @NotNull ResolveInfo resolveInfo) {
    // deserialize defined operator
    serOps.forEach(serOp -> {
      var defVar = state.resolve(serOp.name());
      var opInfo = new OpDecl.OpInfo(serOp.name().name(), serOp.assoc());
      defVar.opDecl = new SerDef.SerOpDecl(opInfo);
    });
    // deserialize renamed operator
    opRename.view().forEach((name, serOp) -> {
      var defVar = state.resolve(name);
      var asName = serOp.name();
      var asAssoc = serOp.assoc();
      var opDecl = new ResolveInfo.RenamedOpDecl(new OpDecl.OpInfo(asName, asAssoc));
      resolveInfo.renameOp(defVar, opDecl, BindBlock.EMPTY, true);
      // ^ always use empty bind block bc we will resolve the bind here!
    });
    // and their bindings
    serOps.view().forEach(serOp -> {
      var defVar = state.resolve(serOp.name());
      var opDecl = defVar.opDecl;
      assert opDecl != null; // just initialized above
      deBindDontCare(resolveInfo, state, opDecl, serOp.bind());
    });
    opRename.view().forEach((name, serOp) -> {
      var defVar = state.resolve(name);
      var asBind = serOp.bind();
      var opDecl = resolveInfo.opRename().get(defVar).component1();
      deBindDontCare(resolveInfo, state, opDecl, asBind);
    });
  }

  private void deBindDontCare(
    @NotNull ResolveInfo resolveInfo,
    @NotNull SerTerm.DeState state,
    @NotNull OpDecl opDecl,
    @NotNull SerDef.SerBind bind
  ) {
    var opSet = resolveInfo.opSet();
    opSet.ensureHasElem(opDecl);
    bind.loosers().forEach(looser -> {
      var target = resolveOp(resolveInfo, state, looser);
      opSet.bind(opDecl, OpDecl.BindPred.Looser, target, SourcePos.SER);
    });
    bind.tighters().forEach(tighter -> {
      var target = resolveOp(resolveInfo, state, tighter);
      opSet.bind(opDecl, OpDecl.BindPred.Tighter, target, SourcePos.SER);
    });
  }

  private @NotNull OpDecl resolveOp(@NotNull ResolveInfo resolveInfo, @NotNull SerTerm.DeState state, @NotNull SerDef.QName name) {
    var original = state.resolve(name);
    var renamed = resolveInfo.opRename().getOrNull(original);
    var opDecl = renamed != null ? renamed.component1() : original.opDecl;
    if (opDecl != null) return opDecl;
    resolveInfo.opSet().reporter.report(new NameProblem.OperatorNameNotFound(SourcePos.SER, name.toString()));
    throw new Context.ResolvingInterruptedException();
  }

  private void de(@NotNull AyaShape.Factory shapeFactory, @NotNull PhysicalModuleContext context, @NotNull SerDef serDef, @NotNull SerTerm.DeState state) {
    var mod = context.moduleName();
    var drop = mod.size();
    var def = serDef.de(state);
    assert def.ref().core != null;
    shapeFactory.bonjour(def);
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
        data.bodies().forEachWith(((DataDef) def).body, (ctor, ctorDef) -> {
          if (isExported(ctor.self())) export(context, drop, ctor.self(), ctorDef.ref);
          export(innerCtx, ctor.self().name(), ctorDef.ref);
        });
        context.importModules(innerCtx.moduleName().drop(drop), Stmt.Accessibility.Public, innerCtx.exports, SourcePos.SER);
      }
      case SerDef.Struct struct -> {
        var innerCtx = context.derive(struct.name().name());
        if (isExported(struct.name())) export(context, struct.name().name(), def.ref());
        struct.fields().forEachWith(((StructDef) def).fields, (field, fieldDef) -> {
          if (isExported(field.self())) export(context, drop, field.self(), fieldDef.ref);
          export(innerCtx, field.self().name(), fieldDef.ref);
        });
        context.importModules(innerCtx.moduleName().drop(drop), Stmt.Accessibility.Public, innerCtx.exports, SourcePos.SER);
      }
      case SerDef.Prim prim -> export(context, prim.name().id, def.ref());
      default -> {}
    }
  }

  private void export(@NotNull PhysicalModuleContext context, int dropMod, @NotNull SerDef.QName qname, DefVar<?, ?> ref) {
    export(context, qname.mod().drop(dropMod), qname.name(), ref);
  }

  private void export(@NotNull PhysicalModuleContext context, @NotNull String name, @NotNull AnyVar var) {
    export(context, ImmutableSeq.empty(), name, var);
  }

  private void export(@NotNull PhysicalModuleContext context, @NotNull ImmutableSeq<String> mod, @NotNull String name, @NotNull AnyVar var) {
    context.exports.getOrPut(Context.TOP_LEVEL_MOD_NAME, ModuleExport::new)
      .export(name, var);
    context.exports.getOrPut(mod, ModuleExport::new)
      .export(name, var);
  }

  private boolean isExported(@NotNull SerDef.QName qname) {
    return exports.contains(qname);
  }
}
