// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.incremental;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.module.ModuleLoader;
import org.aya.syntax.GenericAyaParser;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public class DelegateCompilerAdvisor implements CompilerAdvisor {
  protected final @NotNull CompilerAdvisor delegate;

  public DelegateCompilerAdvisor(@NotNull CompilerAdvisor delegate) { this.delegate = delegate; }

  @Override
  public void notifyIncrementalJob(@NotNull ImmutableSeq<LibrarySource> modified, @NotNull ImmutableSeq<ImmutableSeq<LibrarySource>> affected) {
    delegate.notifyIncrementalJob(modified, affected);
  }

  @Override public boolean isSourceModified(@NotNull LibrarySource source) {
    return delegate.isSourceModified(source);
  }

  @Override public void updateLastModified(@NotNull LibrarySource source) {
    delegate.updateLastModified(source);
  }

  @Override public void prepareLibraryOutput(@NotNull LibraryOwner owner) throws IOException {
    delegate.prepareLibraryOutput(owner);
  }

  @Override public void clearLibraryOutput(@NotNull LibraryOwner owner) throws IOException {
    delegate.clearLibraryOutput(owner);
  }

  @Override public void clearModuleOutput(@NotNull LibrarySource source) throws IOException {
    delegate.clearModuleOutput(source);
  }

  @Override public @NotNull GenericAyaParser createParser(@NotNull Reporter reporter) {
    return delegate.createParser(reporter);
  }

  @Override public @Nullable ResolveInfo
  doLoadCompiledCore(@NotNull Reporter reporter, @NotNull ModulePath mod, @Nullable Path sourcePath, @Nullable Path corePath, @NotNull ModuleLoader recurseLoader) throws IOException, ClassNotFoundException {
    return delegate.doLoadCompiledCore(reporter, mod, sourcePath, corePath, recurseLoader);
  }

  @Override public void
  doSaveCompiledCore(@NotNull LibrarySource file, @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<TyckDef> defs) throws IOException {
    delegate.doSaveCompiledCore(file, resolveInfo, defs);
  }
}
