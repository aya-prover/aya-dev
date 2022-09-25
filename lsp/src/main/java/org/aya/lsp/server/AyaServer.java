// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.lsp.actions.ComputeTerm;
import org.aya.lsp.models.ComputeTermResult;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.utils.Log;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AyaServer implements LanguageClientAware, LanguageServer {
  private final @NotNull AyaService service;

  public AyaServer() {
    this(CompilerAdvisor.inMemory());
  }

  public AyaServer(@NotNull CompilerAdvisor advisor) {
    this.service = new AyaService(advisor);
  }

  @JsonRequest("aya/load")
  @SuppressWarnings("unused")
  public @NotNull CompletableFuture<@NotNull List<HighlightResult>> load(Object uri) {
    return CompletableFuture.supplyAsync(() -> service.reload().asJava());
  }

  @JsonRequest("aya/computeType")
  @SuppressWarnings("unused")
  public @NotNull CompletableFuture<@NotNull ComputeTermResult> computeType(ComputeTermResult.Params input) {
    return CompletableFuture.supplyAsync(() -> service.computeTerm(input, ComputeTerm.Kind.type()));
  }

  @JsonRequest("aya/computeNF")
  @SuppressWarnings("unused")
  public @NotNull CompletableFuture<@NotNull ComputeTermResult> computeNF(ComputeTermResult.Params input) {
    return CompletableFuture.supplyAsync(() -> service.computeTerm(input, ComputeTerm.Kind.nf()));
  }

  @Override public void connect(@NotNull LanguageClient client) {
    var c = (AyaLanguageClient) client;
    Log.init(c);
    service.connect(c);
  }
}
