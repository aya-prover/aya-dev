// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.primitive.PrimFactory;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.FileModuleLoader;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.AyaFiles;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.reporter.CountingReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * This module loader is used to load source/compiled modules in a library.
 * For modules belonging to this library, both compiled/source modules are searched and loaded (compiled first).
 * For modules belonging to dependencies, only compiled modules are searched and loaded.
 * This is archived by not storing source dirs of dependencies to
 * {@link LibraryOwner#modulePath()} and always trying to find compiled cores in
 * {@link LibraryOwner#outDir()}.
 *
 * <p>
 * Unlike {@link FileModuleLoader}, this loader only resolves the module rather than tycking it.
 * Tyck decision is made by {@link LibraryCompiler} by judging whether the source file
 * is modified since last build.
 *
 * @author kiva
 * @see FileModuleLoader
 */
record LibraryModuleLoader(
  @NotNull CountingReporter reporter,
  @NotNull LibraryOwner owner,
  @NotNull CompilerAdvisor advisor,
  @NotNull LibraryModuleLoader.United states
) implements ModuleLoader {
  @Override public @NotNull ResolveInfo
  load(@NotNull ModulePath mod, @NotNull ModuleLoader recurseLoader) {
    var basePaths = owner.modulePath();
    var sourcePath = AyaFiles.resolveAyaSourceFile(basePaths, mod.module());
    if (sourcePath == null) {
      // We are loading a module belonging to dependencies, find the compiled core.
      // The compiled core should always exist, otherwise the dependency is not built.
      // TODO: what if module name clashes?
      var depCorePath = AyaFiles.resolveAyaCompiledFile(basePaths, mod.module());
      var core = loadCompiledCore(mod, depCorePath, depCorePath, recurseLoader);
      assert core != null : "dependencies not built?";
      return core;
    }

    var source = owner.findModule(mod);
    assert source != null;

    // we are loading a module belonging to this library, try finding compiled core first.
    // If found, check modifications and decide whether to proceed with compiled core.
    var corePath = source.compiledCorePath();
    var tryCore = loadCompiledCore(mod, sourcePath, corePath, recurseLoader);
    if (tryCore != null) {
      // the core file was found and up-to-date.
      source.resolveInfo().set(tryCore);
      return tryCore;
    }

    // No compiled core is found, or source file is modified, compile it from source.
    var program = source.program().get();
    assert program != null;
    var context = new EmptyContext(reporter, sourcePath).derive(mod);
    var resolveInfo = resolveModule(states.primFactory, context, program, recurseLoader);
    source.resolveInfo().set(resolveInfo);
    return tyckModule(resolveInfo, (moduleResolve, defs) -> {
      source.notifyTycked(moduleResolve, defs);
      if (reporter.noError()) saveCompiledCore(source, moduleResolve, defs);
    });
  }

  @Override
  public boolean existsFileLevelModule(@NotNull ModulePath path) {
    return owner.findModule(path) != null;
  }

  private @Nullable ResolveInfo loadCompiledCore(
    @NotNull ModulePath mod, @Nullable Path sourcePath,
    @Nullable Path corePath, @NotNull ModuleLoader recurseLoader
  ) {
    return advisor.loadCompiledCore(reporter, mod, sourcePath, corePath, recurseLoader);
  }

  private void saveCompiledCore(
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<TyckDef> defs
  ) {
    advisor.saveCompiledCore(file, resolveInfo, defs);
  }

  record United(@NotNull PrimFactory primFactory) {
  }
}
