// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import com.javax0.sourcebuddy.Compiler;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.FileSerializer;
import org.aya.compiler.ModuleSerializer;
import org.aya.compiler.TermExprializer;
import org.aya.generic.NameGenerator;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.resolve.module.ModuleCallback;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.TopLevelDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.term.AppTerm;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.LocalTerm;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.util.error.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

public class CompileTest {
  @Test public void test0() {
    var result = tyck("""
      open data Nat | O | S Nat
      open data Vec (n : Nat) Type
      | O, A   => vnil
      | S n, A => vcons A (Vec n A)

      def plus (a b : Nat) : Nat elim a
      | O => b
      | S n => S (plus n b)
      """); // .filter(x -> x instanceof FnDef || x instanceof DataDef);

    var code = serializeFrom(result);

    System.out.println(code);

    try {
      var clazz = Compiler.java().from("AYA.baka", code).compile().load().get();
      var loader = clazz.getClassLoader();

      var fieldO = loader.loadClass("AYA.baka$Nat$O").getField("INSTANCE");
      var fieldS = loader.loadClass("AYA.baka$Nat$S").getField("INSTANCE");
      var fieldPlus = loader.loadClass("AYA.baka$plus").getField("INSTANCE");
      fieldO.setAccessible(true);
      fieldS.setAccessible(true);
      fieldPlus.setAccessible(true);
      var O = (JitCon) fieldO.get(null);
      var S = (JitCon) fieldS.get(null);
      var plus = (JitFn) fieldPlus.get(null);
      var zero = new ConCall(O, ImmutableSeq.empty(), 0, ImmutableSeq.empty());
      var one = new ConCall(S, ImmutableSeq.empty(), 0, ImmutableSeq.of(zero));
      var two = new ConCall(S, ImmutableSeq.empty(), 0, ImmutableSeq.of(one));
      var three = new ConCall(S, ImmutableSeq.empty(), 0, ImmutableSeq.of(two));

      var mResult = plus.invoke(zero, ImmutableSeq.of(two, three));
      System.out.println(mResult.debuggerOnlyToString());
    } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException | Compiler.CompileException e) {
      throw new RuntimeException(e);
    }

    // var vec = (DataDef) result.findFirst(def -> def.ref().name().equals("Vec")).get();
    // var out = new DataSerializer(new StringBuilder(), 0, new NameGenerator(), _ -> {}).serialize(vec).result();
    // System.out.println("Vec.java");
    // System.out.println(out);
    //
    // var vnil = (ConDef) result.findFirst(def -> def.ref().name().equals("[]")).get();
    // out = new ConSerializer(new StringBuilder(), 0, new NameGenerator()).serialize(vnil).result();
    // System.out.println("vnil.java");
    // System.out.println(out);
    //
    // var plus = (FnDef) result.findFirst(def -> def.ref().name().equals("plus")).get();
    // out = new FnSerializer(new StringBuilder(), 0, new NameGenerator()).serialize(plus).result();
    // System.out.println("plus.java");
    // System.out.println(out);
  }

  @Test public void serLam() {
    // \ t. (\0. 0 t)
    var lam = new LamTerm(new Closure.Jit(t -> new LamTerm(new Closure.Idx(new AppTerm(new LocalTerm(0), t)))));
    var out = new TermExprializer(new NameGenerator(), ImmutableSeq.empty())
      .serialize(lam)
      .result();

    System.out.println(out);
  }

  public record TyckResult(@NotNull ImmutableSeq<TyckDef> defs, @NotNull ResolveInfo info) {

  }

  private static final @NotNull Path FILE = Path.of("/home/senpai/1919810.aya");
  public static final ThrowingReporter REPORTER = new ThrowingReporter(AyaPrettierOptions.pretty());

  public static @NotNull String serializeFrom(@NotNull TyckResult result) {
    return new FileSerializer(result.info.shapeFactory())
      .serialize(new FileSerializer.FileResult(null, new ModuleSerializer.ModuleResult(
        DumbModuleLoader.DUMB_MODULE_NAME, result.defs.filterIsInstance(TopLevelDef.class), ImmutableSeq.empty())))
      .result();
  }

  public static TyckResult tyck(@Language("Aya") @NotNull String code) {
    var moduleLoader = new DumbModuleLoader(new EmptyContext(REPORTER, FILE));
    var callback = new ModuleCallback<RuntimeException>() {
      ImmutableSeq<TyckDef> ok;
      @Override public void onModuleTycked(@NotNull ResolveInfo x, @NotNull ImmutableSeq<TyckDef> defs) { ok = defs; }
    };
    var info = moduleLoader.tyckModule(moduleLoader.resolve(new AyaParserImpl(REPORTER).program(
      new SourceFile("<baka>", FILE, code))), callback);
    return new TyckResult(callback.ok, info);
  }
}
