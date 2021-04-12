// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp;

import org.aya.api.error.DelayedReporter;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.cli.CompilerFlags;
import org.aya.cli.SingleFileCompiler;
import org.aya.pretty.doc.Doc;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

public class AyaService implements WorkspaceService, TextDocumentService {
  private final Buffer<Path> modulePath = Buffer.of();
  private final DelayedReporter reporter = new DelayedReporter(this::reportError);
  private final SingleFileCompiler compiler = new SingleFileCompiler(reporter, null);

  private void reportError(@NotNull Problem problem) {
  }

  public void registerLibrary(@NotNull Path path) {
    // TODO[kiva]: work with Library System when it is finished
    modulePath.append(path);
  }

  @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    var compilerFlags = new CompilerFlags(
      CompilerFlags.Message.EMOJI, false, null,
      modulePath.toImmutableSeq()
    );

    for (var change : params.getChanges()) {
      var uri = Path.of(URI.create(change.getUri()));
      try {
        int status = compiler.compile(uri, compilerFlags, defs -> {
          // TODO[kiva]: typed syntax highlight
          Log.i("Compiled %s", uri.toAbsolutePath());
        });
        reporter.reportString("Compiler finished with code " + status);
      } catch (IOException e) {
        reporter.report(new LspIOError(uri));
      }
    }
    reporter.reportNow();
  }

  @Override public void didOpen(DidOpenTextDocumentParams params) {
    Log.d("didOpen: " + params.getTextDocument().getUri());
  }

  @Override public void didChange(DidChangeTextDocumentParams params) {
    // TODO: incremental compilation?
  }

  @Override public void didClose(DidCloseTextDocumentParams params) {
    Log.d("didClose: " + params.getTextDocument().getUri());
  }

  @Override public void didSave(DidSaveTextDocumentParams params) {
    Log.d("didSave: " + params.getTextDocument().getUri());
  }

  @Override public void didChangeConfiguration(DidChangeConfigurationParams params) {
  }

  static record LspIOError(@NotNull Path file) implements Problem {
    @Override public @NotNull SourcePos sourcePos() {
      return SourcePos.NONE;
    }

    @Override public @NotNull Doc describe() {
      return Doc.plain("Unable to read file: " + file.toAbsolutePath());
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}
