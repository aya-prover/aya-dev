// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.util.NormalizeMode;
import org.aya.cli.repl.command.Command;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface ReplCommands {
  record Code(@NotNull String code) {
  }

  record HelpItem(@NotNull String cmd) {
  }

  @NotNull Command CHANGE_PROMPT = new Command(ImmutableSeq.of("prompt"), "Change the REPL prompt text") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl, @NotNull String argument) {
      repl.config.prompt = argument;
      return Result.ok("Changed prompt to `" + argument + "`", true);
    }
  };

  @NotNull Command SHOW_TYPE = new Command(ImmutableSeq.of("type"), "Show the type of the given expression") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl, @NotNull Code code) {
      var type = repl.replCompiler.compileExpr(code.code(), repl.config.normalizeMode);
      return type != null ? new Result(Output.stdout(repl.render(type)), true)
        : Result.err("Failed to get expression type", true);
    }
  };

  @NotNull Command LOAD_FILE = new Command(ImmutableSeq.of("load"), "Load file into REPL") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl, @NotNull Path path) {
      try {
        repl.replCompiler.loadToContext(path);
      } catch (IOException e) {
        return Result.err("Unable to read file: " + e.getLocalizedMessage(), true);
      }
      // SingleFileCompiler would print result to REPL.
      return new Result(Output.empty(), true);
    }
  };

  @NotNull Command CHANGE_CWD = new Command(ImmutableSeq.of("cd"), "Change current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl, @NotNull Path path) {
      if (!Files.isDirectory(path)) return Result.err("cd: no such file or directory: " + path, true);
      repl.cwd = path;
      // for jline completer to work properly, but it does not have any effect actually
      System.setProperty("user.dir", path.toAbsolutePath().toString());
      return new Result(Output.empty(), true);
    }
  };

  @NotNull Command PRINT_CWD = new Command(ImmutableSeq.of("pwd"), "Print current working directory") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl) {
      return new Result(Output.stdout(repl.cwd.toAbsolutePath().toString()), true);
    }
  };

  @NotNull Command CHANGE_PP_WIDTH = new Command(ImmutableSeq.of("print-width"), "Set printed output width") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl, @Nullable Integer width) {
      if (width == null) return Result.err("print-width: invalid width", true);
      repl.prettyPrintWidth = width;
      return Result.ok("Printed output width set to " + width, true);
    }
  };

  @NotNull Command QUIT = new Command(ImmutableSeq.of("quit", "exit"), "Quit the REPL") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl) {
      return Result.ok("See you space cow woof woof :3", false);
    }
  };

  @NotNull Command CHANGE_NORM_MODE = new Command(ImmutableSeq.of("normalize"), "Set or display the normalization mode") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl, @Nullable NormalizeMode normalizeMode) {
      if (normalizeMode == null) return Result.ok("Normalization mode: " + repl.config.normalizeMode, true);
      else {
        repl.config.normalizeMode = normalizeMode;
        return Result.ok("Normalization mode set to " + normalizeMode, true);
      }
    }
  };

  @NotNull Command TOGGLE_UNICODE = new Command(ImmutableSeq.of("unicode"), "Enable or disable unicode in REPL output") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl, @Nullable Boolean enable) {
      var enableUnicode = enable != null ? enable : !repl.config.enableUnicode;
      repl.config.enableUnicode = enableUnicode;
      return Result.ok("Toggled Unicode to be " + (enableUnicode ? "enabled" : "disabled"), true);
    }
  };

  @NotNull Command HELP = new Command(ImmutableSeq.of("?", "help"), "Describe a selected command or show all commands") {
    @Entry public @NotNull Command.Result execute(@NotNull Repl repl, @Nullable ReplCommands.HelpItem argument) {
      if (argument != null && !argument.cmd.isEmpty()) {
        var cmd = repl.commandManager.cmd.find(c -> c.owner().names().contains(argument.cmd));
        if (cmd.isDefined()) return Result.ok(cmd.get().owner().help(), true);
        else return Result.err("No such command: " + argument.cmd, true);
      }
      var commands = Doc.vcat(repl.commandManager.cmd.view()
        .map(command -> Doc.sep(
          Doc.commaList(command.owner().names().map(name -> Doc.plain(Command.PREFIX + name))),
          Doc.plain("-"),
          Doc.english(command.owner().help())
        )));
      return new Result(new Output(commands, Doc.empty()), true);
    }
  };
}
