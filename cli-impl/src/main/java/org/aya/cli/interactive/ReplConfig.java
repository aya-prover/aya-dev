// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.interactive;

import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonParseException;
import kala.control.Option;
import org.aya.cli.utils.LiteratePrettierOptions;
import org.aya.generic.AyaHome;
import org.aya.syntax.literate.CodeOptions.NormalizeMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReplConfig implements AutoCloseable {
  public transient final Option<Path> configFile;
  public @NotNull String prompt = "> ";
  public @NotNull NormalizeMode normalizeMode = NormalizeMode.FULL;
  public @NotNull LiteratePrettierOptions literatePrettier = new LiteratePrettierOptions();
  public boolean enableUnicode = true;
  /** Disables welcome message, echoing info, etc. */
  public boolean silent = false;

  public ReplConfig(@NotNull Option<Path> file) {
    this.configFile = file;
  }

  public static @NotNull ReplConfig loadFromDefault() throws IOException, JsonParseException {
    return ReplConfig.loadFrom(AyaHome.ayaHome().resolve("repl_config.json"));
  }

  public static @NotNull ReplConfig loadFrom(@NotNull Path file) throws IOException, JsonParseException {
    if (Files.notExists(file)) return new ReplConfig(Option.some(file));
    return loadFrom(Option.some(file), Files.readString(file));
  }

  @VisibleForTesting
  public static @NotNull ReplConfig loadFrom(@NotNull Option<Path> file, @NotNull String jsonText) throws JsonParseException {
    var config = LiteratePrettierOptions.gsonBuilder(new GsonBuilder())
      .registerTypeAdapter(ReplConfig.class, (InstanceCreator<ReplConfig>) type -> new ReplConfig(file))
      .create()
      .fromJson(jsonText, ReplConfig.class);
    if (config == null) return new ReplConfig(file);
    config.literatePrettier.checkDeserialization();
    return config;
  }

  @Override public void close() throws IOException {
    if (configFile.isDefined()) {
      var gson = LiteratePrettierOptions.gsonBuilder(new GsonBuilder()).create();
      Files.writeString(configFile.get(), gson.toJson(this));
    }
  }
}
