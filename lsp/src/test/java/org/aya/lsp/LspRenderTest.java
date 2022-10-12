// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.aya.lsp.models.RenderOptions;
import org.aya.lsp.models.ServerOptions;
import org.intellij.lang.annotations.Language;
import org.javacs.lsp.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.aya.lsp.tester.TestCommand.compile;
import static org.junit.jupiter.api.Assertions.*;

public class LspRenderTest extends LspTesterBase {
  public static final @NotNull String FILE_NAME = "LspRenderTestData.aya";

  @Test
  @SuppressWarnings("OptionalGetWithoutIsPresent")
  public void testHoverRender() {
    var param = new InitializeParams();
    var folder = new WorkspaceFolder();
    @Language("JSON") String extParam = """
    {
      "colorScheme": {
        "aya:Keyword":     "0x0033B3",
        "aya:FnCall":      "0x00627A",
        "aya:Generalized": "0x00627A",
        "aya:DataCall":    "0xDCDCAA",
        "aya:StructCall":  "0xDCDCAA",
        "aya:ConCall":     "0x067D17",
        "aya:FieldCall":   "0x871094"
      },
      "styleFamily": null,
      "renderTarget": "Debug"
    }
    """;

    folder.uri = TEST_LIB.toUri();
    folder.name = "TEST_LIB";
    param.workspaceFolders = List.of(folder);
    param.initializationOptions = new Gson().fromJson(extParam, JsonElement.class);

    var lsp = launch(param);
    lsp.execute(
      compile((a, e) -> { })
    );

    var resultRaw = lsp.service.hover(
      new TextDocumentPositionParams(new TextDocumentIdentifier(
        TEST_LIB.resolve("src").resolve(FILE_NAME).toUri()), new Position(4, 4)));

    lsp.service.updateOptions(new ServerOptions(null, null, RenderOptions.RenderTarget.TeX));

    var resultTeX = lsp.service.hover(
      new TextDocumentPositionParams(new TextDocumentIdentifier(
        TEST_LIB.resolve("src").resolve(FILE_NAME).toUri()), new Position(4, 4)));

    assertEquals(
      "(a b : Nat) : Nat",
      resultRaw.get().contents.get(0).value);

    assertEquals(
      "\\noindent(a b : \\textcolor[HTML]{dcdcaa}{Nat}) : \\textcolor[HTML]{dcdcaa}{Nat}",
      resultTeX.get().contents.get(0).value);

    // I won't test HTML here...
  }

  @Test
  public void testParseColor() {
    var code0 = "#0B45E0";
    var code1 = "0x19198A";
    var code2 = "000000";

    assertEquals(0x0B45E0, RenderOptions.parseColor(code0).get());
    assertEquals(0x19198A, RenderOptions.parseColor(code1).get());
    assertEquals(0x000000, RenderOptions.parseColor(code2).get());

    var badCode0 = "#0B45E";
    var badCode1 = "0x1919810";
    var badCode2 = "IMKIVA";

    assertFalse(RenderOptions.parseColor(badCode0).isOk());
    assertFalse(RenderOptions.parseColor(badCode1).isOk());
    assertFalse(RenderOptions.parseColor(badCode2).isOk());
  }
}
