// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple3;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.stmt.BindBlock;
import org.aya.concrete.stmt.Stmt;
import org.aya.concrete.stmt.UseHide;
import org.aya.core.def.PrimDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.repr.CodeShape;
import org.aya.ref.DefVar;
import org.aya.resolve.context.ModuleContext;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.order.TyckOrder;
import org.aya.tyck.trace.Trace;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.aya.util.terck.MutableGraph;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param primFactory  (global) all primitives shared among all modules in a compilation task.
 * @param shapeFactory (scoped per file/ResolveInfo) {@link CodeShape} that are discovered during tycking this module, modified by tycker.
 * @param opSet        (scoped per file/ResolveInfo) binary operators.
 * @param opRename     rename-as-operators, only stores names that renamed in current module (and re-exported ops).
 * @param imports      modules imported using `import` command.
 * @param reExports    modules re-exported using `public open` command.
 * @param depGraph     dependency graph of definitions. for each (v, successors) in the graph,
 *                     `successors` should be tycked first.
 */
@Debug.Renderer(text = "thisModule.moduleName().joinToString(\"::\")")
public record ResolveInfo(
  @NotNull ModuleContext thisModule,
  @NotNull ImmutableSeq<Stmt> program,
  @NotNull PrimDef.Factory primFactory,
  @NotNull AyaShape.Factory shapeFactory,
  @NotNull AyaBinOpSet opSet,
  @NotNull MutableMap<DefVar<?, ?>, Tuple3<RenamedOpDecl, BindBlock, Boolean>> opRename,
  @NotNull MutableMap<ImmutableSeq<String>, ResolveInfo> imports,
  @NotNull MutableMap<ImmutableSeq<String>, UseHide> reExports,
  @NotNull MutableGraph<TyckOrder> depGraph
) {
  public ResolveInfo(
    @NotNull PrimDef.Factory primFactory,
    @NotNull AyaShape.Factory shapeFactory,
    @NotNull AyaBinOpSet opSet,
    @NotNull ModuleContext thisModule,
    @NotNull ImmutableSeq<Stmt> thisProgram
  ) {
    this(thisModule, thisProgram, primFactory, shapeFactory, opSet,
      MutableMap.create(), MutableMap.create(),
      MutableMap.create(), MutableGraph.create());
  }

  public ResolveInfo(
    @NotNull PrimDef.Factory primFactory,
    @NotNull ModuleContext thisModule,
    @NotNull ImmutableSeq<Stmt> thisProgram
  ) {
    this(primFactory, new AyaShape.Factory(), new AyaBinOpSet(thisModule.reporter()), thisModule, thisProgram);
  }

  /**
   * @param definedHere Is this operator renamed in this module, or publicly renamed by upstream?
   * @see #open(ResolveInfo, SourcePos, Stmt.Accessibility)
   */
  public void renameOp(@NotNull DefVar<?, ?> defVar, @NotNull RenamedOpDecl renamed, @NotNull BindBlock bind, boolean definedHere) {
    defVar.opDeclRename.put(thisModule().moduleName(), renamed);
    opRename.put(defVar, Tuple.of(renamed, bind, definedHere));
  }

  public void open(@NotNull ResolveInfo other, @NotNull SourcePos sourcePos, @NotNull Stmt.Accessibility acc) {
    // open defined operator and their bindings
    opSet().importBind(other.opSet(), sourcePos);
    // open discovered shapes as well
    shapeFactory().importAll(other.shapeFactory());
    // open renamed operators and their bindings
    other.opRename().forEach((defVar, tuple) -> {
      if (acc == Stmt.Accessibility.Public) {
        // if it is `public open`, make renamed operators transitively accessible by storing
        // them in my `opRename` bc "my importers" cannot access `other.opRename`.
        // see: https://github.com/aya-prover/aya-dev/issues/519
        renameOp(defVar, tuple.component1(), tuple.component2(), false);
      } else defVar.opDeclRename.put(thisModule().moduleName(), tuple.component1());
    });
  }

  public @NotNull ExprTycker newTycker(@NotNull Reporter reporter, Trace.@Nullable Builder builder) {
    return new ExprTycker(primFactory, shapeFactory, reporter, builder);
  }

  @Debug.Renderer(text = "opInfo.name()")
  public record RenamedOpDecl(@NotNull OpInfo opInfo) implements OpDecl {
  }
}
