// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.morphism.asm.AsmJavaBuilder;
import org.aya.compiler.free.morphism.asm.AsmOutputCollector;
import org.aya.compiler.free.morphism.source.SourceClassBuilder;
import org.aya.compiler.free.morphism.source.SourceCodeBuilder;
import org.aya.compiler.free.morphism.source.SourceFreeJavaBuilder;
import org.aya.compiler.serializers.AyaSerializer;
import org.aya.compiler.serializers.ModuleSerializer;
import org.aya.compiler.serializers.TermExprializer;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.resolve.module.ModuleCallback;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitDef;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.TopLevelDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.AppTerm;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.LocalTerm;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.util.FileUtil;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.constant.ConstantDescs;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.aya.compiler.serializers.NameSerializer.getClassName;

public class CompileTest {
  public static final @NotNull @Language("Aya") String SAMPLE_CODE = """
    open inductive Nat | zro | suc Nat
    open inductive Vec Nat Type
    | zro, A => vnil
    | suc n, A => vcons A (Vec n A)
    
    def plus (a b : Nat) : Nat
    | zro, b => b
    | suc a, b => suc (plus a b)
    
    def id {A : Type} (a : A) : A => a
    def idLam : Nat -> Nat => id (fn n => n)
    """;

  public void justTest(@NotNull CompileTester tester) throws ClassNotFoundException, NoSuchFieldException {
    var baka = DumbModuleLoader.DUMB_MODULE_NAME;

    JitCon O = tester.loadInstance(getClassName(baka.derive("Nat"), "zro"));
    JitCon S = tester.loadInstance(getClassName(baka.derive("Nat"), "suc"));
    JitFn plus = tester.loadInstance(getClassName(baka, "plus"));
    JitFn idLam = tester.loadInstance(getClassName(baka, "idLam"));
    var zero = new ConCall(O, ImmutableSeq.empty(), 0, ImmutableSeq.empty());
    var one = new ConCall(S, ImmutableSeq.empty(), 0, ImmutableSeq.of(zero));
    var two = new ConCall(S, ImmutableSeq.empty(), 0, ImmutableSeq.of(one));
    var three = new ConCall(S, ImmutableSeq.empty(), 0, ImmutableSeq.of(two));

    var mResult = plus.invoke(ImmutableSeq.of(two, three));
    var idLamResult = idLam.invoke(ImmutableSeq.empty());
    var finalResult = new AppTerm(idLamResult, mResult).make();
    System.out.println(finalResult.easyToString());
  }

  @Test public void test0() {
    var result = tyck(SAMPLE_CODE); // .filter(x -> x instanceof FnDef || x instanceof DataDef);

    var code = serializeFrom(result);

    try {
      var tester = new CompileTester(code);
      tester.compile();
      justTest(tester);
    } catch (IOException | ClassNotFoundException | NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  @Test public void serLam() {
    var fjb = SourceFreeJavaBuilder.create();
    var dummy = new SourceCodeBuilder(new SourceClassBuilder(fjb, ConstantDescs.CD_Object, fjb.sourceBuilder()), fjb.sourceBuilder());
    // \ t. (\0. 0 t)
    var lam = new LamTerm(new Closure.Jit(t -> new LamTerm(new Closure.Locns(new AppTerm(new LocalTerm(0), t)))));
    var out = new TermExprializer(dummy, ImmutableSeq.empty(), new ModuleSerializer.MatchyRecorder())
      .serialize(lam);

    System.out.println(out);
  }

  public record TyckResult(@NotNull ImmutableSeq<TyckDef> defs, @NotNull ResolveInfo info) { }

  private static final @NotNull Path FILE = Path.of("/home/senpai/1919810.aya");
  public static final ThrowingReporter REPORTER = new ThrowingReporter(AyaPrettierOptions.pretty());

  public static @NotNull String serializeFrom(@NotNull TyckResult result) {
    return new ModuleSerializer(result.info.shapeFactory())
      .serializeWithBestBuilder(new ModuleSerializer.ModuleResult(
        DumbModuleLoader.DUMB_MODULE_NAME, result.defs.filterIsInstance(TopLevelDef.class)));
  }

  @Test
  public void testAsm() throws IOException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    var base = Path.of("src", "test", "build");
    var result = tyck(SAMPLE_CODE);

    FileUtil.deleteRecursively(base);
    new ModuleSerializer(result.info.shapeFactory())
      .serialize(new AsmJavaBuilder<>(new AsmOutputCollector.Default()), new ModuleSerializer.ModuleResult(
        DumbModuleLoader.DUMB_MODULE_NAME, result.defs.filterIsInstance(TopLevelDef.class)))
      .writeTo(base);

    try (var innerLoader = new URLClassLoader(new URL[]{base.toUri().toURL()}, getClass().getClassLoader())) {
      justTest(new CompileTester(innerLoader));
    }
  }

  public static TyckResult tyck(@Language("Aya") @NotNull String code) {
    var moduleLoader = new DumbModuleLoader(new EmptyContext(REPORTER, FILE));
    var callback = new ModuleCallback<RuntimeException>() {
      ImmutableSeq<TyckDef> ok;
      @Override public void onModuleTycked(@NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<TyckDef> defs) { ok = defs; }
    };
    var info = moduleLoader.tyckModule(moduleLoader.resolve(new AyaParserImpl(REPORTER).program(
      new SourceFile("<baka>", FILE, code))), callback);
    return new TyckResult(callback.ok, info);
  }
}
