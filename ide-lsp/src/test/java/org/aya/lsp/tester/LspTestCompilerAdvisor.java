// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.tester;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.incremental.InMemoryCompilerAdvisor;
import org.aya.cli.library.source.LibrarySource;
import org.aya.core.def.GenericDef;
import org.aya.core.serde.Serializer;
import org.aya.resolve.ResolveInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class LspTestCompilerAdvisor extends InMemoryCompilerAdvisor {
  public @Nullable ImmutableSeq<ImmutableSeq<LibrarySource>> lastJob;
  public final @NotNull MutableList<ResolveInfo> newlyCompiled = MutableList.create();

  public @NotNull SeqView<LibrarySource> lastCompiled() {
    var lastJob = this.lastJob;
    if (lastJob == null) return SeqView.empty();
    return lastJob.view().flatMap(Function.identity());
  }

  public void mutate(@NotNull LibrarySource source) {
    coreTimestamp.remove(timestampKey(source));
  }

  public void prepareCompile() {
    lastJob = null;
    newlyCompiled.clear();
  }

  @Override
  public void notifyIncrementalJob(@NotNull ImmutableSeq<LibrarySource> modified, @NotNull ImmutableSeq<ImmutableSeq<LibrarySource>> affected) {
    this.lastJob = affected;
  }

  @Override
  public void doSaveCompiledCore(Serializer.@NotNull State serState, @NotNull LibrarySource file, @NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<GenericDef> defs) {
    super.doSaveCompiledCore(serState, file, resolveInfo, defs);
    newlyCompiled.append(resolveInfo);
  }
}
