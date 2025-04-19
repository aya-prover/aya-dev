// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import kala.collection.immutable.ImmutableSeq;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.AyaParserImpl;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.EmptyContext;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.resolve.module.ModuleCallback;
import org.aya.syntax.core.def.TyckDef;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.ThrowingReporter;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class TestUtils {

  private static final @NotNull Path FILE = Path.of("");
  public static final ThrowingReporter REPORTER = new ThrowingReporter(AyaPrettierOptions.pretty());

  public record TyckResult(@NotNull ImmutableSeq<TyckDef> defs, @NotNull ResolveInfo info) { }

  public static TyckResult tyck(@Language("Aya") @NotNull String code) {
    var moduleLoader = new DumbModuleLoader(new EmptyContext(REPORTER, FILE));
    var callback = new ModuleCallback<RuntimeException>() {
      ImmutableSeq<TyckDef> ok;
      @Override public void onModuleTycked(@NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<TyckDef> defs) { ok = defs; }
    };
    var module = new AyaParserImpl(REPORTER).program(new SourceFile("<tyck>", FILE, code));
    var info = moduleLoader.tyckModule(moduleLoader.resolve(module), callback);
    return new TyckResult(callback.ok, info);
  }
}
