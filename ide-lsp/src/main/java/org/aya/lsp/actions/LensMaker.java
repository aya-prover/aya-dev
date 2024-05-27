// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.Resolver;
import org.aya.ide.action.FindReferences;
import org.aya.ide.syntax.SyntaxDeclAction;
import org.aya.lsp.utils.LspRange;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.decl.Decl;
import org.javacs.lsp.CodeLens;
import org.javacs.lsp.Command;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public record LensMaker(
  @NotNull SeqView<LibraryOwner> libraries,
  @NotNull MutableList<CodeLens> codeLens
) implements SyntaxDeclAction {
  public static @NotNull List<CodeLens> invoke(@NotNull LibrarySource source, @NotNull SeqView<LibraryOwner> libraries) {
    var maker = new LensMaker(libraries, MutableList.create());
    var program = source.program().get();
    if (program != null) program.forEach(maker);
    return maker.codeLens.asJava();
  }

  public static @NotNull CodeLens resolve(@NotNull CodeLens codeLens) {
    var cmd = new Gson().fromJson((JsonElement) codeLens.data, Command.class);
    return new CodeLens(codeLens.range, cmd, codeLens.data);
  }

  @Override public void accept(@NotNull Stmt stmt) {
    if (stmt instanceof Decl maybe) {
      Resolver.withChildren(maybe).forEach(dv -> {
        var refs = FindReferences.findRefsOutsideDefs(SeqView.of(dv), libraries).toImmutableSeq();
        if (!refs.isEmpty()) {
          var sourcePos = dv.concrete.sourcePos();
          var uri = LspRange.toFileUri(sourcePos);
          var range = LspRange.toRange(sourcePos);

          // https://code.visualstudio.com/api/references/commands
          // editor.action.showReferences (or vscode.executeReferenceProvider) - Execute all reference providers.
          //   uri - Uri of a text document
          //   position - A position in a text document
          //   (returns) - A promise that resolves to an array of Location-instances.
          var title = refs.sizeEquals(1) ? "1 usage" : "%d usages".formatted(refs.size());
          var locations = refs.mapNotNull(LspRange::toLoc).asJava();
          var cmd = uri.getOrElse(
            it -> new Command(title, "editor.action.showReferences", List.of(it, range.end, locations)),
            () -> new Command(title, "", Collections.emptyList()));
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
          codeLens.append(new CodeLens(range, null, cmd));
        }
      });
    }
    SyntaxDeclAction.super.accept(stmt);
  }
}
