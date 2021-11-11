// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.NormalizeMode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReplConfig implements AutoCloseable {
  public transient final Path configFile;
  public @NotNull String prompt = "> ";
  public @NotNull NormalizeMode normalizeMode = NormalizeMode.NF;
  public @NotNull DistillerOptions distillerOptions = DistillerOptions.pretty();
  public boolean enableUnicode = true;

  public ReplConfig(@NotNull Path file) {
    this.configFile = file;
  }

  private void checkInitialization() {
    if (distillerOptions.map.isEmpty()) distillerOptions.reset();
  }

  public static @NotNull ReplConfig loadFrom(@NotNull Path file) throws IOException {
    if (Files.notExists(file)) return new ReplConfig(file);
    var config = new GsonBuilder()
      .registerTypeAdapter(ReplConfig.class, (InstanceCreator<ReplConfig>) type -> new ReplConfig(file))
      .create()
      .fromJson(Files.newBufferedReader(file), ReplConfig.class);
    if (config == null) return new ReplConfig(file);
    config.checkInitialization();
    return config;
  }

  @Override public void close() throws IOException {
    var json = new Gson().toJson(this);
    Files.writeString(configFile, json);
  }
}
