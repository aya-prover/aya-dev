// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import org.aya.cli.render.RenderOptions;
import org.aya.compiler.serializers.FnSerializer;
import org.aya.compiler.serializers.ModuleSerializer;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.producer.AyaParserImpl;
import org.aya.repl.Command;
import org.aya.repl.CommandArg;
import org.aya.repl.ReplUtil;
import org.aya.syntax.compile.JitDef;
import org.aya.syntax.core.def.*;
import org.aya.syntax.literate.CodeOptions;
import org.aya.syntax.ref.AnyDefVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface ReplCommands {
  record Code(@NotNull String code) { }
  record Prompt(@NotNull String prompt) { }

  record ColorParam(@NotNull Either<RenderOptions.ColorSchemeName, Path> value)
    implements CommandArg.ArgEither<RenderOptions.ColorSchemeName, Path> { }

  record StyleParam(@NotNull Either<RenderOptions.StyleFamilyName, Path> value)
    implements CommandArg.ArgEither<RenderOptions.StyleFamilyName, Path> { }

  @NotNull Command CHANGE_PROMPT = new Command(ImmutableSeq.of("prompt"), "Change the REPL prompt text") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Prompt argument) {
      var prompt = argument.prompt;
      if (prompt.startsWith("\"") && prompt.endsWith("\"")) prompt = prompt.substring(1, prompt.length() - 1);
      repl.config.prompt = prompt;
      return Result.ok("Changed prompt to `" + prompt + "`", true);
    }
  };

  @NotNull Command SHOW_TYPE = new Command(ImmutableSeq.of("type"), "Show the type of the given expression") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Code code) {
      var type = repl.replCompiler.computeType(code.code(), repl.config.normalizeMode);
      return type != null ? new Result(Output.stdout(repl.render(type)), true)
        : Result.err("Failed to get expression type", true);
    }
  };

  @NotNull Command SHOW_ANF = new FnCommand(ImmutableSeq.of("compile-anf"), "Show the compiled function in A-Normal Form") {
    @Override public @NotNull Result executeFn(@NotNull AyaRepl repl, FnDef fn) {
      var ser = new FnSerializer(repl.replCompiler.getShapeFactory(), new ModuleSerializer.MatchyRecorder());
      var method = ser.buildInvokeForPrettyPrint(fn);
      return new Result(Output.stdout(method.toDoc()), true);
    }
  };

  @NotNull Command SHOW_MCT = new FnCommand(ImmutableSeq.of("case-tree"), "Show the case tree of a pattern-matching function") {
    @Override public @NotNull Result executeFn(@NotNull AyaRepl repl, FnDef fn) {
      if (!(fn.body() instanceof Either.Right(var body))) return Result.err("Not a pattern-matching function", true);
      var classes = body.classes.map(cl ->
        Doc.sep(Doc.commaList(cl.term().view().map(t -> t.toDoc(repl.prettierOptions()))),
          Doc.symbol("=>"),
          Doc.commaList(cl.cls().mapToObj(Doc::ordinal))));
      return new Result(Output.stdout(Doc.vcat(classes)), true);
    }
  };

  @NotNull Command SHOW_INFO = new Command(ImmutableSeq.of("info"), "Show the information of the given definition") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Code code) {
      var resolved = repl.replCompiler.parseToAnyVar(code.code);
      if (!(resolved instanceof AnyDefVar defVar)) return Result.err("Not a valid reference", true);
      var def = AnyDef.fromVar(defVar);
      AnyDef topLevel = def;
      switch (def) {
        case ConDefLike conDefLike -> topLevel = conDefLike.dataRef();
        case MemberDefLike memberDefLike -> topLevel = memberDefLike.classRef();
        default -> {
        }
      }

      return new Result(Output.stdout((switch (topLevel) {
        case JitDef jitDef -> repl.render(jitDef);
        case TyckAnyDef<?> tyckDef -> repl.render(tyckDef.core());
      })), true);
    }
  };

  @NotNull Command SHOW_PARSE_TREE = new Command(ImmutableSeq.of("parse-tree"), "Show the parse tree of the given expression") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Code code) {
      var parseTree = new AyaParserImpl(repl.replCompiler.reporter).parseNode(code.code());
      return Result.ok(parseTree.toDebugString(), true);
    }
  };

  @NotNull Command SHOW_SHAPES = new Command(ImmutableSeq.of("debug-show-shapes"), "Show recognized shapes") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl) {
      var discovered = repl.replCompiler.getShapeFactory().discovered;
      return new Result(Output.stdout(Doc.vcat(discovered.mapTo(MutableList.create(),
        (def, recog) ->
          Doc.sep(BasePrettier.refVar(def),
            Doc.symbol("=>"),
            Doc.plain(recog.shape().name()))))), true);
    }
  };

  @NotNull Command LOAD = new Command(ImmutableSeq.of("load"), "Load a file or library into REPL") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Path path) {
      try {
        repl.replCompiler.loadToContext(path);
      } catch (IOException e) {
        return Result.err("Unable to load file or library: " + e.getLocalizedMessage(), true);
      }
      // SingleFileCompiler would print result to REPL.
      return new Result(Output.EMPTY, true);
    }
  };

  @NotNull Command UNIMPORT = new Command(ImmutableSeq.of("unimport"), "Remove an imported module from the context") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Code code) {
      var index = repl.replCompiler.imports.indexWhere(ii ->
        ii.modulePath().toString().equals(code.code));
      if (index < 0) return Result.err("Cannot find module after name `" + code.code + "`", true);
      repl.replCompiler.imports.removeAt(index);
      return Result.ok("Removed module `" + code.code + "`", true);
    }
  };

  @NotNull Command CHANGE_CWD = new Command(ImmutableSeq.of("cd"), "Change current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Path path) {
      if (!Files.isDirectory(path)) return Result.err("cd: no such file or directory: " + path, true);
      repl.cwd = path;
      // for jline completer to work properly, but it does not have any effect actually
      System.setProperty("user.dir", path.toAbsolutePath().toString());
      return new Result(Output.EMPTY, true);
    }
  };

  @NotNull Command SHOW_CWD = new Command(ImmutableSeq.of("pwd"), "Show current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl) {
      return new Result(Output.stdout(repl.cwd.toAbsolutePath().toString()), true);
    }
  };
  @NotNull Command SHOW_PROPERTY = new Command(ImmutableSeq.of("system-property"), "Show a system property") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull String key) {
      var property = key.isBlank() ? null : System.getProperty(key);
      var stdout = property != null ? Output.stdout(property) : Output.stderr("No such property");
      return new Result(stdout, true);
    }
  };
  @NotNull Command SHOW_MODULE_PATHS = new Command(ImmutableSeq.of("module-path"), "Show module path(s)") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl) {
      return new Result(Output.stdout(repl.replCompiler.modulePaths.joinToString()), true);
    }
  };

  @NotNull Command CHANGE_PP_WIDTH = new Command(ImmutableSeq.of("print-width"), "Set printed output width") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable Integer width) {
      if (width == null) return Result.err("print-width: invalid width", true);
      repl.prettyPrintWidth = width;
      return Result.ok("Printed output width set to " + width, true);
    }
  };

  @NotNull Command QUIT = new Command(ImmutableSeq.of("quit", "exit"), "Quit the REPL") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl) {
      return Result.ok(repl.config.quiet ? "" :
        "See you space cow woof woof :3", false);
    }
  };

  @NotNull Command CHANGE_NORM_MODE = new Command(ImmutableSeq.of("normalize"), "Set or display the normalization mode") {
    @Entry
    public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable CodeOptions.NormalizeMode normalizeMode) {
      if (normalizeMode == null) return Result.ok("Normalization mode: " + repl.config.normalizeMode, true);
      else {
        repl.config.normalizeMode = normalizeMode;
        return Result.ok("Normalization mode set to " + normalizeMode, true);
      }
    }
  };

  @NotNull Command TOGGLE_PRETTY = new Command(ImmutableSeq.of("print-toggle"), "Toggle a pretty printing option") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable AyaPrettierOptions.Key key) {
      var builder = new StringBuilder();
      var map = repl.prettierOptions().map;
      if (key == null) {
        builder.append("Current pretty printing options:");
        for (var k : AyaPrettierOptions.Key.values())
          builder
            .append("\n").append(k.name()).append(": ")
            .append(map.get(k));
      } else {
        var newValue = !map.get(key);
        map.put(key, newValue);
        builder.append(key.name()).append(" changed to ").append(newValue);
      }
      return Result.ok(builder.toString(), true);
    }
  };

  @NotNull Command TOGGLE_UNICODE = new Command(ImmutableSeq.of("unicode"), "Enable or disable unicode in REPL output") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable Boolean enable) {
      var enableUnicode = enable != null ? enable : !repl.config.enableUnicode;
      repl.config.enableUnicode = enableUnicode;
      return Result.ok("Toggled Unicode to be " + (enableUnicode ? "enabled" : "disabled"), true);
    }
  };

  @NotNull Command HELP = new Command(ImmutableSeq.of("?", "help"), "Describe a selected command or show all commands") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable ReplUtil.HelpItem argument) {
      return ReplUtil.invokeHelp(repl.commandManager, argument);
    }
  };

  @NotNull Command COLOR = new Command(ImmutableSeq.of("color"), "Display the current color scheme or switch to another") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable ColorParam colorParam) {
      var options = repl.config.literatePrettier.renderOptions;
      if (colorParam == null)
        return Result.ok(options.prettyColorScheme(), true);
      var fallback = options.colorScheme;
      var fallbackPath = options.path;
      try {
        options.updateColorScheme(colorParam.value);
        options.stylist(RenderOptions.OutputTarget.Unix); // if there's error, report now.
        return Result.ok(options.prettyColorScheme(), true);
      } catch (IllegalArgumentException | IOException e) {
        options.colorScheme = fallback;
        options.path = fallbackPath;
        return Result.err((e instanceof IOException ? "Problem reading file: " : "") + e.getMessage(), true);
      }
    }
  };

  @NotNull Command STYLE = new Command(ImmutableSeq.of("style"), "Display the current style/Switch to another style") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable StyleParam styleParam) {
      var options = repl.config.literatePrettier.renderOptions;
      if (styleParam == null) return Result.ok(options.prettyStyleFamily(), true);
      var fallback = options.styleFamily;
      try {
        options.updateStyleFamily(styleParam.value);
        options.stylist(RenderOptions.OutputTarget.Unix); // if there's error, report now.
        return Result.ok(options.prettyStyleFamily(), true);
      } catch (IllegalArgumentException | IOException e) {
        options.styleFamily = fallback;
        return Result.err((e instanceof IOException ? "Problem reading file: " : "") + e.getMessage(), true);
      }
    }
  };
}
