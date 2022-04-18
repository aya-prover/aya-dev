// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.utils.AyaCompiler;
import org.aya.core.def.Def;
import org.aya.core.def.PrimDef;
import org.aya.core.serde.CompiledAya;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.generic.Constants;
import org.aya.generic.util.InternalException;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.CachedModuleLoader;
import org.aya.resolve.module.FileModuleLoader;
import org.aya.resolve.module.ModuleLoader;
import org.aya.util.FileUtil;
import org.aya.util.reporter.CountingReporter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
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
  @NotNull LibraryModuleLoader.United states
  ) implements ModuleLoader {
  private void saveCompiledCore(@NotNull LibrarySource file, @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<Def> defs) {
    try {
      var coreFile = file.coreFile();
      AyaCompiler.saveCompiledCore(coreFile, resolveInfo, defs, states.ser);
      Timestamp.update(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override public @NotNull ResolveInfo
  load(@NotNull ImmutableSeq<@NotNull String> mod, @NotNull ModuleLoader recurseLoader) {
    var basePaths = owner.modulePath();
    var sourcePath = FileUtil.resolveFile(basePaths, mod, ".aya");
    if (sourcePath == null) {
      // We are loading a module belonging to dependencies, find the compiled core.
      // The compiled core should always exist, otherwise the dependency is not built.
      var depCorePath = FileUtil.resolveFile(basePaths, mod, Constants.AYAC_POSTFIX);
      assert depCorePath != null : "dependencies not built?";
      return loadCompiledCore(mod, depCorePath, depCorePath, recurseLoader);
    }

    var source = owner.findModule(mod);
    assert source != null;

    // we are loading a module belonging to this library, try finding compiled core first.
    // If found, check modifications and decide whether to proceed with compiled core.
    var corePath = FileUtil.resolveFile(owner.outDir(), mod, Constants.AYAC_POSTFIX);
    if (Files.exists(corePath)) {
      return source.resolveInfo().value = loadCompiledCore(mod, corePath, sourcePath, recurseLoader);
    }

    // No compiled core is found, or source file is modified, compile it from source.
    var program = source.program().value;
    assert program != null;
    var context = new EmptyContext(reporter(), sourcePath).derive(mod);
    var resolveInfo = resolveModule(states.primFactory, context, program, recurseLoader);
    source.resolveInfo().value = resolveInfo;
    return tyckModule(null, resolveInfo, (moduleResolve, defs) -> {
      source.tycked().value = defs;
      if (reporter().noError()) saveCompiledCore(source, moduleResolve, defs);
    });
  }

  private @NotNull ResolveInfo loadCompiledCore(
    @NotNull ImmutableSeq<String> mod, @NotNull Path corePath,
    @NotNull Path sourcePath, @NotNull ModuleLoader recurseLoader
  ) {
    assert recurseLoader instanceof CachedModuleLoader<?>;
    var context = new EmptyContext(reporter(), sourcePath).derive(mod);
    try (var inputStream = FileUtil.ois(corePath)) {
      var compiledAya = (CompiledAya) inputStream.readObject();
      return compiledAya.toResolveInfo(recurseLoader, context, states().de());
    } catch (IOException | ClassNotFoundException e) {
      throw new InternalException("Compiled aya found but cannot be loaded", e);
    }
  }

  record United(@NotNull SerTerm.DeState de, @NotNull Serializer.State ser, @NotNull PrimDef.Factory primFactory) {
    public United(@NotNull PrimDef.Factory primFactory) {
      this(new SerTerm.DeState(primFactory), new Serializer.State(), primFactory);
    }
  }
}
