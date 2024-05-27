// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.mutable.MutableMap;
import org.aya.generic.stmt.TyckOrder;
import org.aya.primitive.PrimFactory;
import org.aya.primitive.ShapeFactory;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.salt.AyaBinOpSet;
import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.UseHide;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.CompiledVar;
import org.aya.syntax.ref.DefVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.TyckState;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.aya.util.terck.MutableGraph;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @param thisModule   context of the underlying module
 * @param primFactory  globally shared prim definition data
 * @param shapeFactory shapes local to this module
 * @param opSet        operators local to this module
 * @param opRename     open/import renames with operators
 * @param depGraph     local to this module
 */
@Debug.Renderer(text = "thisModule.moduleName().joinToString(\"::\")")
public record ResolveInfo(
  @NotNull ModuleContext thisModule,
  @NotNull PrimFactory primFactory,
  @NotNull ShapeFactory shapeFactory,
  @NotNull AyaBinOpSet opSet,
  @NotNull MutableMap<AnyDef, OpRenameInfo> opRename,
  @NotNull MutableMap<ModuleName.Qualified, ImportInfo> imports,
  @NotNull MutableMap<ModuleName.Qualified, UseHide> reExports,
  @NotNull MutableGraph<TyckOrder> depGraph
) {
  public ResolveInfo(
    @NotNull ModuleContext thisModule,
    @NotNull PrimFactory primFactory,
    @NotNull ShapeFactory shapeFactory
  ) {
    this(thisModule, primFactory, shapeFactory, new AyaBinOpSet(thisModule.reporter()));
  }
  public ResolveInfo(
    @NotNull ModuleContext thisModule,
    @NotNull PrimFactory primFactory,
    @NotNull ShapeFactory shapeFactory,
    @NotNull AyaBinOpSet opSet
  ) {
    this(thisModule, primFactory, shapeFactory, opSet,
      MutableMap.create(), MutableMap.create(), MutableMap.create(), MutableGraph.create());
  }
  public ExprTycker newTycker() { return newTycker(opSet.reporter); }
  public ExprTycker newTycker(@NotNull Reporter reporter) {
    return new ExprTycker(makeTyckState(), reporter);
  }
  public @NotNull TyckState makeTyckState() {
    return new TyckState(shapeFactory, primFactory);
  }

  public record ImportInfo(@NotNull ResolveInfo resolveInfo, boolean reExport) { }
  public record OpRenameInfo(
    @NotNull Context bindCtx, @NotNull RenamedOpDecl renamed,
    @NotNull BindBlock bind, boolean reExport
  ) { }

  public @Nullable OpDecl resolveOpDecl(AnyVar var) {
    return switch (var) {
      case CompiledVar jit -> resolveOpDecl(jit.core());
      case DefVar<?, ?> ref -> resolveOpDecl(new TyckAnyDef<>(ref));
      default -> null;
    };
  }
  public @NotNull OpDecl resolveOpDecl(AnyDef defVar) {
    var renameInfo = opRename.getOrNull(defVar);
    return renameInfo != null ? renameInfo.renamed() : defVar;
  }

  /**
   * @param reExport if this operator is renamed in this module, then true,
   *                 or if publicly renamed by upstream, then false.
   * @see #open(ResolveInfo, SourcePos, Stmt.Accessibility)
   */
  public void renameOp(
    @NotNull Context bindCtx, @NotNull AnyDef defVar,
    @NotNull RenamedOpDecl renamed, @NotNull BindBlock bind, boolean reExport
  ) {
    // TODO: what if already exists?
    opRename.put(defVar, new OpRenameInfo(bindCtx, renamed, bind, reExport));
  }

  public void open(@NotNull ResolveInfo other, @NotNull SourcePos sourcePos, @NotNull Stmt.Accessibility acc) {
    // open defined operator and their bindings
    opSet.importBind(other.opSet, sourcePos);
    // open discovered shapes as well
    shapeFactory.importAll(other.shapeFactory);
    // open renamed operators and their bindings
    other.opRename.forEach((defVar, tuple) -> {
      // if it is `public open`, make renamed operators transitively accessible by storing
      // them in my `opRename` bc "my importers" cannot access `other.opRename`.
      // see: https://github.com/aya-prover/aya-dev/issues/519
      renameOp(thisModule, defVar, tuple.renamed, tuple.bind, acc != Stmt.Accessibility.Public);
    });
  }

  @Debug.Renderer(text = "opInfo.name()")
  public record RenamedOpDecl(@NotNull OpInfo opInfo) implements OpDecl { }
}
