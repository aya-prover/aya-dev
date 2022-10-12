// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import kala.value.MutableValue;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.incremental.DelegateCompilerAdvisor;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.library.source.MutableLibraryOwner;
import org.aya.cli.single.CompilerFlags;
import org.aya.generic.Constants;
import org.aya.generic.util.AyaFiles;
import org.aya.lsp.actions.*;
import org.aya.lsp.library.WsLibrary;
import org.aya.lsp.models.ComputeTermResult;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.models.RenderOptions;
import org.aya.lsp.models.ServerOptions;
import org.aya.lsp.prim.LspPrimFactory;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.pretty.doc.Doc;
import org.aya.util.FileUtil;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.BufferReporter;
import org.aya.util.reporter.Problem;
import org.javacs.lsp.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AyaLanguageServer implements LanguageServer {
  private static final @NotNull CompilerFlags FLAGS = new CompilerFlags(CompilerFlags.Message.EMOJI, false, false, null, SeqView.empty(), null);

  private final BufferReporter reporter = new BufferReporter();
  private final @NotNull MutableList<LibraryOwner> libraries = MutableList.create();
  /**
   * When working with LSP, we need to track all previously created Primitives.
   * This is shared per library.
   */
  protected final @NotNull MutableMap<LibraryConfig, LspPrimFactory> primFactories = MutableMap.create();
  private final @NotNull CompilerAdvisor advisor;
  private final @NotNull AyaLanguageClient client;
  // This field might be shared in different threads, and it is mutable during runtime.
  private final @NotNull MutableValue<RenderOptions> renderOptions;

  public AyaLanguageServer(@NotNull CompilerAdvisor advisor, @NotNull AyaLanguageClient client) {
    this.advisor = new CallbackAdvisor(this, advisor);
    this.client = client;
    this.renderOptions = MutableValue.createVolatile(RenderOptions.DEFAULT);
    Log.init(this.client);
  }

  public @NotNull SeqView<LibraryOwner> libraries() {
    return libraries.view();
  }

  public void registerLibrary(@NotNull Path path) {
    Log.i("Adding library path %s", path);
    if (!tryAyaLibrary(path)) mockLibraries(path);
  }

  private boolean tryAyaLibrary(@Nullable Path path) {
    if (path == null) return false;
    var ayaJson = path.resolve(Constants.AYA_JSON);
    if (!Files.exists(ayaJson)) return tryAyaLibrary(path.getParent());
    try {
      var config = LibraryConfigData.fromLibraryRoot(path);
      var owner = DiskLibraryOwner.from(config);
      libraries.append(owner);
    } catch (IOException e) {
      var s = new StringWriter();
      e.printStackTrace(new PrintWriter(s));
      Log.e("Cannot load library. Stack trace:\n%s", s.toString());
    }
    // stop retrying and mocking
    return true;
  }

  private void mockLibraries(@NotNull Path path) {
    libraries.appendAll(AyaFiles.collectAyaSourceFiles(path, 1)
      .map(WsLibrary::mock));
  }

  @Override public void initialized() {
    // Imitate the javacs lsp
    // client.registerCapability("workspace/didChangeWatchedFiles");
  }

  @Override public List<TextEdit> willSaveWaitUntilTextDocument(WillSaveTextDocumentParams params) {
    throw new UnsupportedOperationException();
  }

  @Override public InitializeResult initialize(InitializeParams params) {
    var cap = new ServerCapabilities();
    cap.textDocumentSync = 0;
    var workOps = new ServerCapabilities.WorkspaceFoldersOptions(true, true);
    var workCap = new ServerCapabilities.WorkspaceServerCapabilities(workOps);
    cap.completionProvider = new ServerCapabilities.CompletionOptions(
      true, Collections.singletonList("QWERTYUIOPASDFGHJKLZXCVBNM.qwertyuiopasdfghjklzxcvbnm+-*/_[]:"));
    cap.workspace = workCap;
    cap.definitionProvider = true;
    cap.referencesProvider = true;
    cap.hoverProvider = true;
    cap.renameProvider = new ServerCapabilities.RenameOptions(true);
    cap.documentHighlightProvider = true;
    cap.codeLensProvider = new ServerCapabilities.CodeLensOptions(true);
    cap.inlayHintProvider = true;
    cap.documentSymbolProvider = true;
    cap.workspaceSymbolProvider = true;
    cap.foldingRangeProvider = true;

    var folders = params.workspaceFolders;
    // In case we open a single file, this value will be null, so be careful.
    // Make sure the library to be initialized when loading files.
    if (folders != null) folders.forEach(f ->
      registerLibrary(Path.of(f.uri)));

    performInitializeOptions(params);

    return new InitializeResult(cap);
  }

  /**
   * @param param see {@link ServerOptions}
   */
  private void performInitializeOptions(@NotNull InitializeParams param) {
    var options = param.initializationOptions;
    if (options == null) return;

    try {
      var opts = new Gson().fromJson(options, ServerOptions.class);
      updateOptions(opts);
    } catch (JsonParseException ex) {
      // TODO[hoshino]: warn or panic?
      // Do warn
      Log.e("Unable to convert an InitializeParams.initializationOptions to a ServerOptions, cause: %s", ex.getLocalizedMessage());
    }
  }

  private @Nullable LibraryOwner findOwner(@Nullable Path path) {
    if (path == null) return null;
    var ayaJson = path.resolve(Constants.AYA_JSON);
    if (!Files.exists(ayaJson)) return findOwner(path.getParent());
    return libraries.find(lib -> lib.underlyingLibrary().libraryRoot().equals(path)).getOrNull();
  }

  private @Nullable LibrarySource find(@NotNull LibraryOwner owner, Path moduleFile) {
    var found = owner.librarySources().find(src -> src.file().equals(moduleFile));
    if (found.isDefined()) return found.get();
    for (var dep : owner.libraryDeps()) {
      var foundDep = find(dep, moduleFile);
      if (foundDep != null) return foundDep;
    }
    return null;
  }

  public @Nullable LibrarySource find(@NotNull Path moduleFile) {
    for (var lib : libraries) {
      var found = find(lib, moduleFile);
      if (found != null) return found;
    }
    return null;
  }

  public @Nullable LibrarySource find(@NotNull URI uri) {
    return find(toPath(uri));
  }

  @NotNull private Path toPath(@NotNull URI uri) {
    return FileUtil.canonicalize(Path.of(uri));
  }

  public @NotNull ImmutableSeq<HighlightResult> reload() {
    return libraries().flatMap(this::loadLibrary).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<HighlightResult> loadLibrary(@NotNull LibraryOwner owner) {
    Log.i("Loading library %s", owner.underlyingLibrary().name());
    // start compiling
    reporter.clear();
    var primFactory = primFactory(owner);
    try {
      LibraryCompiler.newCompiler(primFactory, reporter, FLAGS, advisor, owner).start();
    } catch (IOException e) {
      var s = new StringWriter();
      e.printStackTrace(new PrintWriter(s));
      Log.e("IOException occurred when running the compiler. Stack trace:\n%s", s.toString());
    }
    publishProblems(reporter, DistillerOptions.pretty());
    return SyntaxHighlight.invoke(owner);
  }

  public void publishProblems(@NotNull BufferReporter reporter, @NotNull DistillerOptions options) {
    var diags = reporter.problems().stream()
      .filter(p -> p.sourcePos().belongsToSomeFile())
      .peek(p -> Log.d("%s", p.describe(options).debugRender()))
      .flatMap(p -> Stream.concat(Stream.of(p), p.inlineHints(options).stream().map(t -> new InlineHintProblem(p, t))))
      .flatMap(p -> p.sourcePos().file().underlying().stream().map(uri -> Tuple.of(uri, p)))
      .collect(Collectors.groupingBy(
        t -> t._1,
        Collectors.mapping(t -> t._2, ImmutableSeq.factory())
      ));
    var from = ImmutableMap.from(diags);
    client.publishAyaProblems(from, options);
  }

  private void clearProblems(@NotNull ImmutableSeq<ImmutableSeq<LibrarySource>> affected) {
    var files = affected.flatMap(i -> i.map(LibrarySource::file));
    client.clearAyaProblems(files);
  }

  @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    params.changes.forEach(change -> {
      switch (change.type) {
        case FileChangeType.Created -> {
          var newSrc = toPath(change.uri);
          switch (findOwner(newSrc)) {
            case MutableLibraryOwner ownerMut -> {
              Log.d("Created new file: %s, added to owner: %s", newSrc, ownerMut.underlyingLibrary().name());
              ownerMut.addLibrarySource(newSrc);
            }
            case null -> {
              var mock = WsLibrary.mock(newSrc);
              Log.d("Created new file: %s, mocked a library %s for it", newSrc, mock.mockConfig().name());
              libraries.append(mock);
            }
            default -> {}
          }
        }
        case FileChangeType.Deleted -> {
          var src = find(change.uri);
          if (src == null) return;
          Log.d("Deleted file: %s, removed from owner: %s", src.file(), src.owner().underlyingLibrary().name());
          switch (src.owner()) {
            case MutableLibraryOwner owner -> owner.removeLibrarySource(src);
            case WsLibrary owner -> libraries.removeAll(o -> o == owner);
            default -> {}
          }
        }
      }
    });
  }

  @Override public Optional<CompletionList> completion(TextDocumentPositionParams position) {
    return Optional.empty();
  }

  @Override public CompletionItem resolveCompletionItem(CompletionItem params) {
    throw new UnsupportedOperationException();
  }

  @Override public Optional<List<? extends GenericLocation>> gotoDefinition(TextDocumentPositionParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return Optional.empty();
    return Optional.of(GotoDefinition.invoke(source, params.position, libraries.view()));
  }

  @Override public Optional<Hover> hover(TextDocumentPositionParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return Optional.empty();
    var doc = ComputeSignature.invokeHover(source, params.position);
    if (doc.isEmpty()) return Optional.empty();
    var marked = new MarkedString(MarkupKind.PlainText, renderOptions.getValue().render(doc));
    return Optional.of(new Hover(List.of(marked)));
  }

  @Override
  public Optional<SignatureHelp> signatureHelp(TextDocumentPositionParams params) {
    throw new UnsupportedOperationException();
  }

  @Override public Optional<List<Location>> findReferences(ReferenceParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return Optional.empty();
    return Optional.of(FindReferences.invoke(source, params.position, libraries.view()));
  }

  @Override public WorkspaceEdit rename(RenameParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return null;
    var renames = Rename.rename(source, params.position, params.newName, libraries.view());
    return new WorkspaceEdit(renames);
  }

  @Override public List<TextEdit> formatting(DocumentFormattingParams params) {
    throw new UnsupportedOperationException();
  }

  @Override public Optional<RenameResponse> prepareRename(TextDocumentPositionParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return Optional.empty();
    var begin = Rename.prepare(source, params.position);
    if (begin == null) return Optional.empty();
    return Optional.of(new RenameResponse(LspRange.toRange(begin.sourcePos()), begin.data()));
  }

  @Override public List<DocumentHighlight> documentHighlight(TextDocumentPositionParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return Collections.emptyList();
    var currentFile = Option.ofNullable(source.file());
    return FindReferences.findOccurrences(source, params.position, SeqView.of(source.owner()))
      // only highlight references in the current file
      .filter(pos -> pos.file().underlying().equals(currentFile))
      .map(pos -> new DocumentHighlight(LspRange.toRange(pos), DocumentHighlightKind.Read))
      .stream().toList();
  }

  @Override public List<CodeLens> codeLens(CodeLensParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return Collections.emptyList();
    return LensMaker.invoke(source, libraries.view());
  }

  @Override public CodeLens resolveCodeLens(CodeLens codeLens) {
    return LensMaker.resolve(codeLens);
  }

  @Override public List<? extends GenericDocumentSymbol> documentSymbol(DocumentSymbolParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return Collections.emptyList();
    return ProjectSymbol.invoke(source)
      .map(ProjectSymbol.Symbol::document)
      .asJava();
  }

  @Override public List<? extends GenericWorkspaceSymbol> workspaceSymbols(WorkspaceSymbolParams params) {
    return ProjectSymbol.invoke(libraries.view())
      .map(ProjectSymbol.Symbol::workspace)
      .asJava();
  }

  @Override
  public List<CodeAction> codeAction(CodeActionParams params) {
    throw new UnsupportedOperationException();
  }

  @Override public List<FoldingRange> foldingRange(FoldingRangeParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return Collections.emptyList();
    return Folding.invoke(source);
  }

  @Override public List<DocumentLink> documentLink(DocumentLinkParams params) {
    throw new UnsupportedOperationException();
  }

  @Override public List<InlayHint> inlayHint(InlayHintParams params) {
    var source = find(params.textDocument.uri);
    if (source == null) return Collections.emptyList();
    return InlayHintMaker.invoke(source, params.range);
  }

  @LspRequest("aya/load") @SuppressWarnings("unused")
  public List<HighlightResult> load(Object uri) {
    return reload().asJava();
  }

  @LspRequest("aya/computeType") @SuppressWarnings("unused")
  public @NotNull ComputeTermResult computeType(ComputeTermResult.Params input) {
    return computeTerm(input, ComputeTerm.Kind.type());
  }

  @LspRequest("aya/computeNF") @SuppressWarnings("unused")
  public @NotNull ComputeTermResult computeNF(ComputeTermResult.Params input) {
    return computeTerm(input, ComputeTerm.Kind.nf());
  }

  public ComputeTermResult computeTerm(@NotNull ComputeTermResult.Params input, ComputeTerm.Kind type) {
    var source = find(input.uri());
    if (source == null) return ComputeTermResult.bad(input);
    return new ComputeTerm(source, type, primFactory(source.owner())).invoke(input);
  }

  private @NotNull LspPrimFactory primFactory(@NotNull LibraryOwner owner) {
    return primFactories.getOrPut(owner.underlyingLibrary(), LspPrimFactory::new);
  }

  @LspRequest("options/updateOptions")
  public void updateOptions(@NotNull ServerOptions options) {
    var renderOpts = RenderOptions.fromServerOptions(options);

    if (renderOpts.isErr()) {
      Log.e("%s", renderOpts.getErr());
    } else {
      this.renderOptions.getAndUpdate(opts -> opts.update(renderOpts.get()));
    }
  }

  public record InlineHintProblem(@NotNull Problem owner, WithPos<Doc> docWithPos) implements Problem {
    @Override public @NotNull SourcePos sourcePos() {
      return docWithPos.sourcePos();
    }

    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return docWithPos.data();
    }

    @Override public @NotNull Severity level() {
      return Severity.INFO;
    }

    @Override public @NotNull Doc brief(@NotNull DistillerOptions options) {
      return describe(DistillerOptions.pretty());
    }
  }

  private static final class CallbackAdvisor extends DelegateCompilerAdvisor {
    private final @NotNull AyaLanguageServer service;

    public CallbackAdvisor(@NotNull AyaLanguageServer service, @NotNull CompilerAdvisor delegate) {
      super(delegate);
      this.service = service;
    }

    @Override
    public void notifyIncrementalJob(@NotNull ImmutableSeq<LibrarySource> modified, @NotNull ImmutableSeq<ImmutableSeq<LibrarySource>> affected) {
      super.notifyIncrementalJob(modified, affected);
      service.clearProblems(affected);
    }
  }
}
