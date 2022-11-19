// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonParseException;
import org.aya.cli.render.Color;
import org.aya.cli.render.RenderOptions;
import org.aya.generic.util.AyaHome;
import org.aya.generic.util.NormalizeMode;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReplConfig implements AutoCloseable {
  public static final @NotNull RenderOptions.OutputTarget DEFAULT_OUTPUT_TARGET = RenderOptions.OutputTarget.Terminal;

  public transient final Path configFile;
  public @NotNull String prompt = "> ";
  public @NotNull NormalizeMode normalizeMode = NormalizeMode.NF;
  public @NotNull DistillerOptions distillerOptions = DistillerOptions.pretty();
  public boolean enableUnicode = true;
  /** Disables welcome message, echoing info, etc. */
  public boolean silent = false;
  public @UnknownNullability RenderOptions renderOptions = new RenderOptions();

  public ReplConfig(@NotNull Path file) {
    this.configFile = file;
  }

  private void checkDeserialization() throws IOException, JsonParseException {
    if (distillerOptions.map.isEmpty()) distillerOptions.reset();
    // maintain the Nullability, renderOptions is probably null after deserializing
    if (renderOptions == null) renderOptions = new RenderOptions();
    renderOptions.checkDeserialization();
    renderOptions.stylist(RenderOptions.OutputTarget.Terminal);
  }

  public static @NotNull ReplConfig loadFromDefault() throws IOException, JsonParseException {
    return ReplConfig.loadFrom(AyaHome.ayaHome().resolve("repl_config.json"));
  }

  public static @NotNull ReplConfig loadFrom(@NotNull Path file) throws IOException, JsonParseException {
    if (Files.notExists(file)) return new ReplConfig(file);
    var config = newGsonBuilder()
      .registerTypeAdapter(ReplConfig.class, (InstanceCreator<ReplConfig>) type -> new ReplConfig(file))
      .create()
      .fromJson(Files.newBufferedReader(file), ReplConfig.class);
    if (config == null) return new ReplConfig(file);
    config.checkDeserialization();
    return config;
  }

  @Override public void close() throws IOException {
    var json = newGsonBuilder()
      .create()
      .toJson(this);
    Files.writeString(configFile, json);
  }

  @VisibleForTesting public static GsonBuilder newGsonBuilder() {
    return new GsonBuilder()
      .registerTypeAdapter(Color.class, new Color.Adapter());
  }
}
