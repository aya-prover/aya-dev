// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.primitive.PrimFactory;
import org.aya.primitive.ShapeFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.StmtResolvers;
import org.aya.resolve.context.Context;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.error.ModNotFoundException;
import org.aya.resolve.salt.AyaBinOpSet;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.ModulePath;
import org.aya.tyck.order.AyaOrgaTycker;
import org.aya.tyck.order.AyaSccTycker;
import org.aya.tyck.tycker.Problematic;
import org.aya.util.reporter.DelayedReporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public interface ModuleLoader extends Problematic {
  default <E extends Exception> @NotNull ResolveInfo tyckModule(
    @NotNull PrimFactory primFactory,
    @NotNull ModuleContext context,
    @NotNull ImmutableSeq<Stmt> program,
    @Nullable ModuleCallback<E> onTycked
  ) throws E, Context.ResolvingInterruptedException {
    var info = resolveModule(primFactory, context, program, this);
    return tyckModule(info, onTycked);
  }

  default <E extends Exception> @NotNull ResolveInfo
  tyckModule(@NotNull ResolveInfo resolveInfo, ModuleCallback<E> onTycked) throws E {
    var SCCs = resolveInfo.depGraph().topologicalOrder();
    var delayedReporter = new DelayedReporter(reporter());
    var sccTycker = new AyaOrgaTycker(AyaSccTycker.create(resolveInfo, delayedReporter), resolveInfo);
    // in case we have un-messaged TyckException
    try (delayedReporter) {
      SCCs.forEach(sccTycker::tyckSCC);
    } finally {
      if (onTycked != null) onTycked.onModuleTycked(
        resolveInfo, sccTycker.sccTycker().wellTyped().toSeq());
    }
    return resolveInfo;
  }

  /**
   * Resolve a certain module
   *
   * @param context       the module
   * @param program       the stmt
   * @param recurseLoader the {@link ModuleLoader} that use for tycking the module
   */
  @ApiStatus.Internal
  default @NotNull ResolveInfo resolveModule(
    @NotNull PrimFactory primFactory,
    @NotNull ModuleContext context,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull ModuleLoader recurseLoader
  ) throws Context.ResolvingInterruptedException {
    var opSet = new AyaBinOpSet(reporter());
    var resolveInfo = new ResolveInfo(context, primFactory, new ShapeFactory(), opSet);
    resolveModule(resolveInfo, program, recurseLoader);
    return resolveInfo;
  }

  /**
   * Resolve a certain module.
   *
   * @param resolveInfo   the context of the module
   * @param program       the statements of the module
   * @param recurseLoader the module loader that used to resolve
   */
  @ApiStatus.Internal
  default void resolveModule(
    @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<Stmt> program,
    @NotNull ModuleLoader recurseLoader
  ) throws Context.ResolvingInterruptedException {
    var resolver = new StmtResolvers(recurseLoader, resolveInfo);
    resolver.resolve(program);
    resolver.desugar(program);

    if (resolver.reporter.dirty()) throw new Context.ResolvingInterruptedException();
  }

  @NotNull ResolveInfo load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader)
    throws Context.ResolvingInterruptedException, ModNotFoundException;
  default @NotNull ResolveInfo load(@NotNull ModulePath path)
    throws Context.ResolvingInterruptedException, ModNotFoundException {
    return load(path, this);
  }

  /**
   * @return if there is a module with path {@param path}, which can be untycked
   */
  boolean existsFileLevelModule(@NotNull ModulePath path);
}
