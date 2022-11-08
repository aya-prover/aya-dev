// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import org.aya.generic.util.AyaHome;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.reporter.IgnoringReporter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReplConfig implements AutoCloseable {
  public transient final Path configFile;
  public @NotNull String prompt = "> ";
  public @NotNull NormalizeMode normalizeMode = NormalizeMode.NF;
  public @NotNull DistillerOptions distillerOptions = DistillerOptions.pretty();
  public @NotNull RenderOptions renderOptions = new RenderOptions(AyaColorScheme.EMACS, AyaStyleFamily.ADAPTIVE_CLI);
  public boolean enableUnicode = true;
  /** Disables welcome message, echoing info, etc. */
  public boolean silent = false;

  public ReplConfig(@NotNull Path file) {
    this.configFile = file;
  }

  private void checkInitialization() {
    if (distillerOptions.map.isEmpty()) distillerOptions.reset();
  }

  public static @NotNull ReplConfig loadFromDefault() throws IOException {
    return ReplConfig.loadFrom(AyaHome.ayaHome().resolve("repl_config.json"));
  }

  public static @NotNull ReplConfig loadFrom(@NotNull Path file) throws IOException {
    if (Files.notExists(file)) return new ReplConfig(file);
    var config = new GsonBuilder()
      .registerTypeAdapter(ReplConfig.class, (InstanceCreator<ReplConfig>) type -> new ReplConfig(file))
      .registerTypeAdapter(RenderOptions.class, new RenderOptions.Deserializer(IgnoringReporter.INSTANCE,
          new RenderOptions(AyaColorScheme.EMACS, AyaStyleFamily.ADAPTIVE_CLI)))
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
