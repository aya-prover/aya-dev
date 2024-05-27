// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.incremental;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.CachedModuleLoader;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.GenericAyaParser;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.Panic;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Advises the compiler to be incremental, helps in-memory analysis,
 * and behaves as the bridge between the library compiler and its caller.
 *
 * @author kiva
 */
public interface CompilerAdvisor {
  static @NotNull CompilerAdvisor onDisk() { return new DiskCompilerAdvisor(); }
  static @NotNull CompilerAdvisor inMemory() { return new InMemoryCompilerAdvisor(); }

  boolean isSourceModified(@NotNull LibrarySource source);
  void updateLastModified(@NotNull LibrarySource source);

  void prepareLibraryOutput(@NotNull LibraryOwner owner) throws IOException;
  void clearLibraryOutput(@NotNull LibraryOwner owner) throws IOException;
  void clearModuleOutput(@NotNull LibrarySource source) throws IOException;

  /** Used for injecting parser from IJ plugin to support on-the-fly analysis. */
  default @NotNull GenericAyaParser createParser(@NotNull Reporter reporter) {
    return new AyaParserImpl(reporter);
  }

  /**
   * Called when all modified sources are detected
   *
   * @param modified User directly modified source files.
   * @param affected SCC of modified sources.
   * @apiNote the SCC is ordered, but DO NOT RELY on it.
   */
  default void notifyIncrementalJob(
    @NotNull ImmutableSeq<LibrarySource> modified,
    @NotNull ImmutableSeq<ImmutableSeq<LibrarySource>> affected
  ) { }

  /**
   * Try to load the compiled core.
   * For {@link DiskCompilerAdvisor}, returns null if the core path does not exist
   * or either source or core path is null.
   * For {@link InMemoryCompilerAdvisor}, returns null if the mod does not store in memory.
   */
  @Nullable ResolveInfo doLoadCompiledCore(
    @NotNull Reporter reporter,
    @NotNull ModulePath mod,
    @Nullable Path sourcePath,
    @Nullable Path corePath,
    @NotNull ModuleLoader recurseLoader
  ) throws IOException, ClassNotFoundException;

  void doSaveCompiledCore(
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<TyckDef> defs
  ) throws IOException;

  @ApiStatus.NonExtendable
  default @Nullable ResolveInfo loadCompiledCore(
    @NotNull Reporter reporter,
    @NotNull ModulePath mod,
    @Nullable Path sourcePath,
    @Nullable Path corePath,
    @NotNull ModuleLoader recurseLoader
  ) {
    assert recurseLoader instanceof CachedModuleLoader<?>;
    try {
      return doLoadCompiledCore(reporter, mod, sourcePath, corePath, recurseLoader);
    } catch (IOException | ClassNotFoundException e) {
      throw new Panic("Compiled aya found but cannot be loaded", e);
    }
  }

  @ApiStatus.NonExtendable
  default void saveCompiledCore(
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<TyckDef> defs
  ) {
    try {
      doSaveCompiledCore(file, resolveInfo, defs);
      updateLastModified(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
