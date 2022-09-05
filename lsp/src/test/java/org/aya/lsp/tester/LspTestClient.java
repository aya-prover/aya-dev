// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.tester;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.generic.Constants;
import org.aya.lsp.server.AyaLanguageClient;
import org.aya.lsp.server.AyaServer;
import org.aya.lsp.server.AyaService;
import org.aya.lsp.utils.Resolver;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class LspTestClient implements AyaLanguageClient {
  public final @NotNull AyaService service;
  public final @NotNull LspTestCompilerAdvisor advisor = new LspTestCompilerAdvisor();

  public LspTestClient() {
    var server = new AyaServer(advisor);
    service = server.getTextDocumentService();
    server.connect(this);
  }

  public void registerLibrary(@NotNull Path libraryRoot) {
    service.registerLibrary(libraryRoot);
  }

  public long loadLibraries() {
    var totalElapsed = 0L;
    for (var lib : service.libraries()) {
      var time = System.currentTimeMillis();
      service.loadLibrary(lib);
      totalElapsed += System.currentTimeMillis() - time;
    }
    return totalElapsed;
  }

  public void execute(@NotNull TestCommand... cmd) {
    for (var c : cmd) executeOne(c);
  }

  private void executeOne(@NotNull TestCommand cmd) {
    switch (cmd) {
      case TestCommand.Mutate m -> {
        var modName = ImmutableSeq.from(Constants.SCOPE_SEPARATOR_PATTERN.split(m.moduleName()));
        var source = Resolver.resolveModule(service.libraries(), modName);
        Assertions.assertTrue(source.isDefined(), "Cannot mutate module " + m.moduleName());
        advisor.mutate(source.get());
        m.checker().check(advisor, Unit.unit());
      }
      case TestCommand.Compile c -> {
        advisor.prepareCompile();
        var elapsed = loadLibraries();
        c.checker().check(advisor, elapsed);
      }
    }
  }

  @Override public void telemetryEvent(Object object) {
  }

  @Override public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
    var errors = diagnostics.getDiagnostics().stream()
      .filter(d -> d.getSeverity() == DiagnosticSeverity.Error)
      .collect(ImmutableSeq.factory());
    Assertions.assertTrue(errors.isEmpty(),
      "Unexpected compiler errors: " +
        errors.joinToString("\n", Diagnostic::getMessage));
  }

  @Override public void showMessage(MessageParams messageParams) {
  }

  @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
    return null;
  }

  @Override public void logMessage(MessageParams message) {
  }
}
