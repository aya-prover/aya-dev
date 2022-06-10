// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.Decl;
import org.aya.concrete.visitor.StmtOps;
import org.aya.lsp.utils.LspRange;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record LensMaker(@NotNull SeqView<LibraryOwner> libraries) implements StmtOps<@NotNull MutableList<CodeLens>> {
  public static @NotNull List<CodeLens> invoke(@NotNull LibrarySource source, @NotNull SeqView<LibraryOwner> libraries) {
    var lens = MutableList.<CodeLens>create();
    var maker = new LensMaker(libraries);
    var program = source.program().value;
    if (program != null) program.forEach(decl -> maker.visit(decl, lens));
    return lens.asJava();
  }

  public static @NotNull CodeLens resolve(@NotNull CodeLens codeLens) {
    var cmd = new Gson().fromJson((JsonElement) codeLens.getData(), Command.class);
    return new CodeLens(codeLens.getRange(), cmd, codeLens.getData());
  }

  @Override
  public void visitDecl(@NotNull Decl decl, @NotNull MutableList<CodeLens> pp) {
    var refs = FindReferences.findRefs(SeqView.of(decl.ref()), libraries).toImmutableSeq();
    if (refs.size() > 0) {
      var range = LspRange.toRange(decl);
      var uri = LspRange.fileUri(decl.sourcePos());
      // https://code.visualstudio.com/api/references/commands
      // editor.action.showReferences (or vscode.executeReferenceProvider) - Execute all reference providers.
      //   uri - Uri of a text document
      //   position - A position in a text document
      //   (returns) - A promise that resolves to an array of Location-instances.
      var cmd = uri.isDefined()
        ? new Command(
        refs.sizeEquals(1) ? "1 usage" : "%d usages".formatted(refs.size()),
        "editor.action.showReferences",
        List.of(uri.get(), range.getEnd(), refs.mapNotNull(LspRange::toLoc).asJava()))
        : null;

      // the type of variable `cmd` is Command, but it cannot be used as
      // the command of the CodeLens created below, because VSCode cannot parse
      // the argument of the command directly due to some Uri serialization problems.
      // see: https://github.com/microsoft/vscode-languageserver-node/issues/495

      // To address the annoying, we use the following workaround:
      // 1. Put the `cmd` as the data field of the CodeLens, so VSCode will not
      //    think the CodeLens _resolved_. A separated resolving stage is required
      //    to make the CodeLens visible to users.
      // 2. In AyaService, register a CodeLens resolver which simply set the command field from data.
      // 3. Together with step2, register a middleware in LSP client (the VSCode side)
      //    to convert Java serialized Uris to vscode.Uri
      pp.append(new CodeLens(range, null, cmd));
    }
    StmtOps.super.visitDecl(decl, pp);
  }
}
