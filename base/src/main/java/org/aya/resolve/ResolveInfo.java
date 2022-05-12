// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.desugar.AyaBinOpSet;
import org.aya.concrete.stmt.BindBlock;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.PrimDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.repr.CodeShape;
import org.aya.resolve.context.ModuleContext;
import org.aya.tyck.order.TyckOrder;
import org.aya.util.MutableGraph;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * @param primFactory     all primitives shared among all modules in a compilation task.
 * @param shapeFactory    {@link CodeShape} that are discovered during tycking this module, modified by tycker.
 * @param opSet           binary operators.
 * @param bindBlockRename bind block for renaming-as-infix operators, see {@link org.aya.ref.DefVar#opDeclRename}.
 * @param imports         modules imported using `import` command.
 * @param reExports       modules re-exported using `public open` command.
 * @param depGraph        dependency graph of definitions. for each (v, successors) in the graph,
 *                        `successors` should be tycked first.
 */
@Debug.Renderer(text = "thisModule.moduleName().joinToString(\"::\")")
public record ResolveInfo(
  @NotNull ModuleContext thisModule,
  @NotNull ImmutableSeq<Stmt> program,
  @NotNull PrimDef.Factory primFactory,
  @NotNull AyaShape.Factory shapeFactory,
  @NotNull AyaBinOpSet opSet,
  @NotNull MutableMap<OpDecl, BindBlock> bindBlockRename,
  @NotNull MutableMap<ImmutableSeq<String>, ResolveInfo> imports,
  @NotNull MutableList<ImmutableSeq<String>> reExports,
  @NotNull MutableGraph<TyckOrder> depGraph
) {
  public ResolveInfo(@NotNull PrimDef.Factory primFactory, @NotNull ModuleContext thisModule, @NotNull ImmutableSeq<Stmt> thisProgram, @NotNull AyaBinOpSet opSet) {
    this(thisModule, thisProgram, primFactory, new AyaShape.Factory(),
      opSet, MutableMap.create(),
      MutableMap.create(), MutableList.create(),
      MutableGraph.create());
  }
}
