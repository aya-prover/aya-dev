// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.cli.render.RenderOptions;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.AyaParserImpl;
import org.aya.repl.Command;
import org.aya.repl.CommandArg;
import org.aya.repl.ReplUtil;
import org.aya.syntax.literate.CodeOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface ReplCommands {
  record Code(@NotNull String code) {}
  record Prompt(@NotNull String prompt) {}

  record ColorParam(@NotNull Either<RenderOptions.ColorSchemeName, Path> value)
    implements CommandArg.ArgEither<RenderOptions.ColorSchemeName, Path> {}

  record StyleParam(@NotNull Either<RenderOptions.StyleFamilyName, Path> value)
    implements CommandArg.ArgEither<RenderOptions.StyleFamilyName, Path> {}

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

  @NotNull Command SHOW_PARSE_TREE = new Command(ImmutableSeq.of("parse-tree"), "Show the parse tree of the given expression") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Code code) {
      var parseTree = new AyaParserImpl(repl.replCompiler.reporter).parseNode(code.code());
      return Result.ok(parseTree.toDebugString(), true);
    }
  };

  @NotNull Command LOAD = new Command(ImmutableSeq.of("load"), "Load a file or library into REPL") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Path path) {
      try {
        repl.replCompiler.loadToContext(path);
      } catch (IOException e) {
        return Result.err(STR."Unable to load file or library: \{e.getLocalizedMessage()}", true);
      }
      // SingleFileCompiler would print result to REPL.
      return new Result(Output.empty(), true);
    }
  };

  @NotNull Command CHANGE_CWD = new Command(ImmutableSeq.of("cd"), "Change current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @NotNull Path path) {
      if (!Files.isDirectory(path)) return Result.err(STR."cd: no such file or directory: \{path}", true);
      repl.cwd = path;
      // for jline completer to work properly, but it does not have any effect actually
      System.setProperty("user.dir", path.toAbsolutePath().toString());
      return new Result(Output.empty(), true);
    }
  };

  @NotNull Command PRINT_CWD = new Command(ImmutableSeq.of("pwd"), "Print current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl) {
      return new Result(Output.stdout(repl.cwd.toAbsolutePath().toString()), true);
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
      return Result.ok(repl.config.silent ? "" :
        "See you space cow woof woof :3", false);
    }
  };

  @NotNull Command CHANGE_NORM_MODE = new Command(ImmutableSeq.of("normalize"), "Set or display the normalization mode") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable CodeOptions.NormalizeMode normalizeMode) {
      if (normalizeMode == null) return Result.ok(STR."Normalization mode: \{repl.config.normalizeMode}", true);
      else {
        repl.config.normalizeMode = normalizeMode;
        return Result.ok(STR."Normalization mode set to \{normalizeMode}", true);
      }
    }
  };

  @NotNull Command TOGGLE_PRETTY = new Command(ImmutableSeq.of("print-toggle"), "Toggle a pretty printing option") {
    @Entry public @NotNull Command.Result execute(@NotNull AyaRepl repl, @Nullable AyaPrettierOptions.Key key) {
      var builder = new StringBuilder();
      var map = repl.config.literatePrettier.prettierOptions.map;
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
