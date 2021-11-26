// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.repl;

import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

public abstract class Command {
  @Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface Entry {
  }

  public static final @NonNls @NotNull String PREFIX = ":";
  public static final @NonNls @NotNull String MULTILINE_BEGIN = ":{";
  public static final @NonNls @NotNull String MULTILINE_END = ":}";

  private final @NotNull ImmutableSeq<String> names;
  private final @NotNull String help;

  public Command(@NotNull ImmutableSeq<String> names, @NotNull String help) {
    this.names = names;
    this.help = help;
  }

  public final @NotNull ImmutableSeq<String> names() {
    return names;
  }

  public final @NotNull String help() {
    return help;
  }

  public record Output(@NotNull Doc stdout, @NotNull Doc stderr) {
    public static @NotNull Output empty() {
      return new Output(Doc.empty(), Doc.empty());
    }

    public static @NotNull Output stdout(@NotNull Doc doc) {
      return new Output(doc, Doc.empty());
    }

    public static @NotNull Output stderr(@NotNull Doc doc) {
      return new Output(Doc.empty(), doc);
    }

    public static @NotNull Output stdout(@NotNull String doc) {
      return new Output(Doc.english(doc), Doc.empty());
    }

    public static @NotNull Output stderr(@NotNull String doc) {
      return new Output(Doc.empty(), Doc.english(doc));
    }
  }

  public record Result(@NotNull Output output, boolean continueRepl) {
    public static @NotNull Command.Result ok(@NotNull String text, boolean continueRepl) {
      return new Result(Output.stdout(Doc.english(text)), continueRepl);
    }

    public static @NotNull Command.Result err(@NotNull String errText, boolean continueRepl) {
      return new Result(Output.stderr(Doc.english(errText)), continueRepl);
    }
  }
}
