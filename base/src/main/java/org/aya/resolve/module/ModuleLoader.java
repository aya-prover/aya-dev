// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.module;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Result;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.StmtResolvers;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.error.LoadErrorKind;
import org.aya.resolve.salt.AyaBinOpSet;
import org.aya.resolve.salt.PatternBinParser;
import org.aya.states.primitive.PrimFactory;
import org.aya.states.primitive.ShapeFactory;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.ref.ModulePath;
import org.aya.tyck.order.AyaOrgaTycker;
import org.aya.tyck.order.AyaSccTycker;
import org.aya.tyck.tycker.Problematic;
import org.aya.util.reporter.ClearableReporter;
import org.aya.util.reporter.DelayedReporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author re-xyr
 */
public interface ModuleLoader extends Problematic {
  @Override @NotNull ClearableReporter reporter();

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
   * TODO: check all caller
   * Resolve a certain module
   *
   * @param context       the module
   * @param program       the stmt
   * @param recurseLoader the {@link ModuleLoader} that use for tycking the module
   * @return null if failed
   */
  @ApiStatus.Internal
  default @Nullable ResolveInfo resolveModule(
    @NotNull PrimFactory primFactory,
    @NotNull ModuleContext context,
    @NotNull ImmutableSeq<Stmt> program,
    @NotNull ModuleLoader recurseLoader
  ) {
    var opSet = new AyaBinOpSet(reporter());
    var resolveInfo = new ResolveInfo(context, primFactory, new ShapeFactory(), opSet);
    var success = resolveModule(resolveInfo, program, recurseLoader);
    if (!success) return null;
    return resolveInfo;
  }

  /// Resolve a certain module.
  ///
  /// @param resolveInfo   the context of the module
  /// @param program       the statements of the module
  /// @param recurseLoader the module loader that used to resolve
  /// @return true if success
  @ApiStatus.Internal
  default boolean resolveModule(
    @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<Stmt> program,
    @NotNull ModuleLoader recurseLoader
  ) {
    var resolver = new StmtResolvers(recurseLoader, resolveInfo);
    resolver.resolve(program);
    try { resolver.desugar(program); } catch (PatternBinParser.MalformedPatternException _) { }

    return !resolver.reporter.dirty();
  }

  /// Load a module with {@param path}
  ///
  /// @return [LoadErrorKind#Resolve] implies a resolve error, and already reported
  ///         [LoadErrorKind#NotFound] implies a module not found error, and not yet reported.
  @NotNull Result<ResolveInfo, LoadErrorKind> load(@NotNull ModulePath path, @NotNull ModuleLoader recurseLoader);
  default @NotNull Result<ResolveInfo, LoadErrorKind> load(@NotNull ModulePath path) {
    return load(path, this);
  }

  /**
   * @return if there is a module with path {@param path}, which can be untycked
   */
  boolean existsFileLevelModule(@NotNull ModulePath path);
}
