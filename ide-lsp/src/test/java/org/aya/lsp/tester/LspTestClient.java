// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.tester;

import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.generic.Constants;
import org.aya.ide.Resolver;
import org.aya.lsp.server.AyaLanguageClient;
import org.aya.lsp.server.AyaLanguageServer;
import org.aya.syntax.ref.ModulePath;
import org.javacs.lsp.DiagnosticSeverity;
import org.javacs.lsp.InitializeParams;
import org.javacs.lsp.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.nio.file.Path;

public final class LspTestClient implements AyaLanguageClient {
  public final @NotNull AyaLanguageServer service;
  public final @NotNull LspTestCompilerAdvisor advisor = new LspTestCompilerAdvisor();

  public LspTestClient() {
    this(new InitializeParams());
  }

  public LspTestClient(@NotNull InitializeParams param) {
    service = new AyaLanguageServer(advisor, this);
    service.initialize(param);
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
        var modName = new ModulePath(ImmutableSeq.from(Constants.SCOPE_SEPARATOR_PATTERN.split(m.moduleName())));
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

  @Override
  public void publishDiagnostics(@NotNull PublishDiagnosticsParams diagnostics) {
    var errors = diagnostics.diagnostics.stream()
      .filter(d -> d.severity == DiagnosticSeverity.Error)
      .collect(ImmutableSeq.factory());
    Assertions.assertTrue(errors.isEmpty(),
      "Unexpected compiler errors: " +
        errors.joinToString("\n", d -> d.message));
  }
}
