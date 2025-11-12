// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp;

import com.google.gson.Gson;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.render.RenderOptions;
import org.aya.generic.Constants;
import org.aya.lsp.models.ProjectPath;
import org.aya.lsp.models.ServerOptions;
import org.aya.lsp.models.ServerRenderOptions;
import org.aya.lsp.server.AyaLanguageServer;
import org.aya.lsp.tester.LspTestClient;
import org.aya.lsp.tester.LspTestCompilerAdvisor;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.decl.FnBody;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.util.FileUtil;
import org.javacs.lsp.InitializeParams;
import org.javacs.lsp.Position;
import org.javacs.lsp.TextDocumentIdentifier;
import org.javacs.lsp.TextDocumentPositionParams;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.aya.lsp.tester.TestCommand.*;
import static org.junit.jupiter.api.Assertions.*;

public class LspTest {
  public static final @NotNull Path RES_DIR = FileUtil.canonicalize(Path.of("src", "test", "resources"));
  public static final @NotNull Path TEST_LIB = RES_DIR.resolve("lsp-test-lib");
  public static final @NotNull Path TEST_LIB0 = RES_DIR.resolve("lsp-test-lib0");
  public static final @NotNull Path TEST_FILE = TEST_LIB0.resolve("unwatched.aya");

  public static @NotNull LspTestClient launch(@NotNull Path libraryRoot) {
    var client = launch();
    client.registerLibrary(libraryRoot);
    return client;
  }

  public static @NotNull LspTestClient launch() {
    return new LspTestClient();
  }

  @Test public void testJustLoad() {
    launch(TEST_LIB).execute(compile((_, _) -> {}));
  }

  @Test public void testIncremental() {
    launch(TEST_LIB).execute(
      compile((_, _) -> {}),
      mutate("StringPrims"),
      compile((a, e) -> assertRemake(a, e, "StringPrims", "HelloWorld"))
    );
  }

  @Test public void test541() {
    launch(TEST_LIB).execute(compile((a, _) -> {
      var testOpt = a.lastCompiled()
        .filter(x -> x.moduleName().module().getLast().equals("VecCore"))
        .flatMap(LibrarySource::program)
        .filterIsInstance(FnDecl.class)
        .filter(x -> x.ref.name().equals("test"))
        .getFirstOption();
      assertFalse(testOpt.isEmpty(), "Do not delete the function called test in Vec");
      var testClause = ((FnBody.BlockBody) testOpt.get().body).clauses().getFirst();
      // vnil, ys => 0
      var testPat = (Pattern.Bind) testClause.patterns.getLast().term().data();
      var testTy = assertInstanceOf(DataCall.class, testPat.theCoreType().get());
      assertNotNull(testTy);
      // ys : Vec A m
      var lastArg = testTy.args().getLast();
      assertFalse(lastArg instanceof MetaPatTerm);
    }));
  }

  @Test public void testRealWorldLike() {
    launch(TEST_LIB).execute(
      compile((_, _) -> {}),
      mutate("HelloWorld"),
      compile((a, e) -> assertRemake(a, e, "HelloWorld")),
      mutate("Nat::Core"),
      compile((a, e) -> assertRemake(a, e, "Nat::Core", "VecCore", "HelloWorld")),
      mutate("PathPrims"),
      compile((a, e) -> assertRemake(a, e, "PathPrims", "Path", "HelloWorld"))
    );
  }

  private void duplicateRegisterTester(int count, @NotNull ProjectPath check, @NotNull AyaLanguageServer lsp) {
    assertEquals(count, lsp.libraries().size());
    assertNotNull(lsp.getRegisteredLibrary(check));
  }

  @Test public void testDuplicateRegister() {

    launch().execute(
      register(TEST_LIB, (_, lsp) ->
        duplicateRegisterTester(1, new ProjectPath.Project(TEST_LIB), lsp)),
      register(TEST_LIB0.resolve(Constants.AYA_JSON), (_, lsp) ->
        duplicateRegisterTester(2, new ProjectPath.Project(TEST_LIB0), lsp)),
      // test dup here
      register(TEST_LIB0, (_, lsp) ->
        duplicateRegisterTester(2, new ProjectPath.Project(TEST_LIB0), lsp)),
      register(TEST_FILE, (_, lsp) ->
        duplicateRegisterTester(3, new ProjectPath.File(TEST_FILE), lsp))
    );
  }

  @Test public void colorful() {
    var initParams = new InitializeParams();
    initParams.initializationOptions = new Gson().toJsonTree(new ServerOptions(new ServerRenderOptions(null, null, RenderOptions.OutputTarget.HTML)));

    var client = new LspTestClient(initParams);
    client.registerLibrary(TEST_LIB);
    client.execute(compile((_, _) -> {}));

    var param = new TextDocumentPositionParams(new TextDocumentIdentifier(
      TEST_LIB.resolve("src/Nat/Core.aya").toUri()),
      new Position(0, 23)
    );

    var result0 = client.service.hover(param);
    assertTrue(result0.isPresent());
    assertEquals("""
        <a href="#Nat-Core-Nat"><span style="color:#218c21;">Nat</span></a>""",
      result0.get().contents.getFirst().value);

    client.service.updateServerOptions(new ServerOptions(new ServerRenderOptions("IntelliJ", null, RenderOptions.OutputTarget.HTML)));

    var result1 = client.service.hover(param);
    assertTrue(result1.isPresent());
    assertEquals("""
        <a href="#Nat-Core-Nat"><span style="color:#000000;">Nat</span></a>""",
      result1.get().contents.getFirst().value);
  }

  private void logTime(long time) {
    System.out.println("Remake changed modules took: " + time + "ms");
  }

  private void assertRemake(@NotNull LspTestCompilerAdvisor advisor, long time, @NotNull String... modules) {
    logTime(time);
    assertNotNull(advisor.lastJob);
    var actualInDep = advisor.newlyCompiled.view()
      .map(r -> r.modulePath().toString())
      .toSeq();
    var actual = advisor.lastCompiled()
      .map(s -> s.moduleName().module().joinToString(Constants.SCOPE_SEPARATOR))
      .concat(actualInDep)
      .distinct()
      .toSeq();
    var expected = ImmutableSeq.from(modules);
    assertEquals(expected.sorted(), actual.sorted());
  }
}
