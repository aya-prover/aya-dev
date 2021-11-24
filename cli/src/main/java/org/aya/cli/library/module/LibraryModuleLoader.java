// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.module;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.CountingReporter;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.LibrarySource;
import org.aya.cli.library.Timestamp;
import org.aya.cli.utils.AyaCompiler;
import org.aya.concrete.resolve.ResolveInfo;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.module.CachedModuleLoader;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.resolve.module.ModuleLoader;
import org.aya.core.def.Def;
import org.aya.core.serde.CompiledAya;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.generic.Constants;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface LibraryModuleLoader extends ModuleLoader {
  @NotNull LibraryCompiler compiler();
  @NotNull LibraryModuleLoader.United states();
  @Override default @NotNull CountingReporter reporter() {
    return compiler().reporter;
  }

  private void saveCompiledCore(@NotNull LibrarySource file, @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<Def> defs) {
    try {
      var coreFile = file.coreFile();
      AyaCompiler.saveCompiledCore(coreFile, resolveInfo, defs, states().ser());
      Timestamp.update(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override default @Nullable ResolveInfo load(@NotNull ImmutableSeq<@NotNull String> mod, @NotNull ModuleLoader recurseLoader) {
    var basePaths = compiler().modulePath();
    var sourcePath = FileUtil.resolveFile(basePaths, mod, ".aya");
    if (sourcePath == null) {
      // We are loading a module belonging to dependencies, find the compiled core.
      // The compiled core should always exist, otherwise the dependency is not built.
      var depCorePath = FileUtil.resolveFile(basePaths, mod, Constants.AYAC_POSTFIX);
      assert depCorePath != null : "dependencies not built?";
      return loadCompiledCore(mod, depCorePath, depCorePath, recurseLoader);
    }

    // we are loading a module belonging to this library, try finding compiled core first.
    // If found, check modifications and decide whether to proceed with compiled core.
    var corePath = FileUtil.resolveFile(compiler().outDir(), mod, Constants.AYAC_POSTFIX);
    if (Files.exists(corePath)) {
      return loadCompiledCore(mod, corePath, sourcePath, recurseLoader);
    }

    // No compiled core is found, or source file is modified, compile it from source.
    var source = compiler().findModuleFile(mod);
    var program = source.program().value;
    assert program != null;
    var context = new EmptyContext(reporter(), sourcePath).derive(mod);
    return tyckModule(context, program, null, (moduleResolve, defs) -> {
      if (reporter().noError()) saveCompiledCore(source, moduleResolve, defs);
    }, recurseLoader);
  }

  private @Nullable ResolveInfo loadCompiledCore(
    @NotNull ImmutableSeq<String> mod, @NotNull Path corePath,
    @NotNull Path sourcePath, @NotNull ModuleLoader recurseLoader
  ) {
    assert recurseLoader instanceof CachedModuleLoader<?>;
    var context = new EmptyContext(reporter(), sourcePath).derive(mod);
    try (var inputStream = FileUtil.ois(corePath)) {
      var compiledAya = (CompiledAya) inputStream.readObject();
      return compiledAya.toResolveInfo(recurseLoader, context, states().de());
    } catch (IOException | ClassNotFoundException e) {
      return null;
    }
  }

  record United(@NotNull SerTerm.DeState de, @NotNull Serializer.State ser) {
    public United() {
      this(new SerTerm.DeState(), new Serializer.State());
    }
  }

  /**
   * This module loader is used to load source/compiled modules in a library.
   * For modules belonging to this library, both compiled/source modules are searched and loaded (compiled first).
   * For modules belonging to dependencies, only compiled modules are searched and loaded.
   * This is archived by not storing source dirs of dependencies to
   * {@link LibraryCompiler#modulePath()} and always trying to find compiled cores in
   * {@link LibraryCompiler#outDir()}.
   *
   * <p>
   * Unlike {@link FileModuleLoader}, this loader only resolves the module rather than tycking it.
   * Tyck decision is made by {@link LibraryCompiler} by judging whether the source file
   * is modified since last build.
   *
   * @author kiva
   * @see FileModuleLoader
   */
  record Impl(
    @Override @NotNull LibraryCompiler compiler,
    @Override @NotNull LibraryModuleLoader.United states
  ) implements LibraryModuleLoader {
  }
}
