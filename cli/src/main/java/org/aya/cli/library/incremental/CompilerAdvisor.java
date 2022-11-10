// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.incremental;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.parse.AyaParserImpl;
import org.aya.concrete.GenericAyaParser;
import org.aya.core.def.GenericDef;
import org.aya.core.serde.SerTerm;
import org.aya.core.serde.Serializer;
import org.aya.generic.util.InternalException;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.CachedModuleLoader;
import org.aya.resolve.module.ModuleLoader;
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
  static @NotNull CompilerAdvisor onDisk() {
    return new DiskCompilerAdvisor();
  }
  static @NotNull CompilerAdvisor inMemory() {
    return new InMemoryCompilerAdvisor();
  }

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
  ) {
  }

  /**
   * Try to load the compiled core.
   * For {@link DiskCompilerAdvisor}, returns null if the core path does not exist
   * or either source or core path is null.
   * For {@link InMemoryCompilerAdvisor}, returns null if the mod does not store in memory.
   */
  @Nullable ResolveInfo doLoadCompiledCore(
    @NotNull SerTerm.DeState deState,
    @NotNull Reporter reporter,
    @NotNull ImmutableSeq<String> mod,
    @Nullable Path sourcePath,
    @Nullable Path corePath,
    @NotNull ModuleLoader recurseLoader
  ) throws IOException, ClassNotFoundException;

  void doSaveCompiledCore(
    @NotNull Serializer.State serState,
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<GenericDef> defs
  ) throws IOException;

  @ApiStatus.NonExtendable
  default @Nullable ResolveInfo loadCompiledCore(
    @NotNull SerTerm.DeState deState,
    @NotNull Reporter reporter,
    @NotNull ImmutableSeq<String> mod,
    @Nullable Path sourcePath,
    @Nullable Path corePath,
    @NotNull ModuleLoader recurseLoader
  ) {
    assert recurseLoader instanceof CachedModuleLoader<?>;
    try {
      return doLoadCompiledCore(deState, reporter, mod, sourcePath, corePath, recurseLoader);
    } catch (IOException | ClassNotFoundException e) {
      throw new InternalException("Compiled aya found but cannot be loaded", e);
    }
  }

  @ApiStatus.NonExtendable
  default void saveCompiledCore(
    @NotNull Serializer.State serState,
    @NotNull LibrarySource file,
    @NotNull ResolveInfo resolveInfo,
    @NotNull ImmutableSeq<GenericDef> defs
  ) {
    try {
      doSaveCompiledCore(serState, file, resolveInfo, defs);
      updateLastModified(file);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
