// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl;

import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonParseException;
import org.aya.cli.repl.render.Color;
import org.aya.cli.repl.render.RenderOptions;
import org.aya.generic.util.AyaHome;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.backend.string.StringStylist;
import org.aya.pretty.backend.string.style.UnixTermStylist;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReplConfig implements AutoCloseable {
  public transient final Path configFile;
  public @NotNull String prompt = "> ";
  public @NotNull NormalizeMode normalizeMode = NormalizeMode.NF;
  public @NotNull DistillerOptions distillerOptions = DistillerOptions.pretty();
  public boolean enableUnicode = true;
  /** Disables welcome message, echoing info, etc. */
  public boolean silent = false;
  /**
   * DO NOT modify this directly, use setRenderOptions instead.
   */
  public @UnknownNullability RenderOptions renderOptions = new RenderOptions();
  public transient @NotNull UnixTermStylist stylist;
  public static final UnixTermStylist DEFAULT_STYLIST = new UnixTermStylist(AyaColorScheme.EMACS, AyaStyleFamily.ADAPTIVE_CLI);

  public ReplConfig(@NotNull Path file) {
    this.configFile = file;
    this.stylist = DEFAULT_STYLIST;
  }

  private void checkInitialization() throws JsonParseException {
    if (distillerOptions.map.isEmpty()) distillerOptions.reset();

    // maintain the Nullability, renderOptions is probably null after deserializing
    if (renderOptions == null) renderOptions = new RenderOptions();
    try {
      renderOptions.checkInitialize();
      stylist = new UnixTermStylist(renderOptions.buildColorScheme(), renderOptions.buildStyleFamily());
    } catch (IOException | JsonParseException ex) {
      // don't halt loading
      // use default stylist but not change the user's settings.
      // TODO: report error but don't stop
      stylist = DEFAULT_STYLIST;
    }
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
    config.checkInitialization();
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

  public void setRenderOptions(@NotNull RenderOptions options) throws IOException, JsonParseException {
    this.stylist = new UnixTermStylist(options.buildColorScheme(), options.buildStyleFamily());
    this.renderOptions = options;
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Contract(" -> new") public @NotNull RenderOptions clone() {
    var newOne = new RenderOptions();

    newOne.colorScheme = this.renderOptions.colorScheme;
    newOne.styleFamily = this.renderOptions.styleFamily;
    newOne.path = this.renderOptions.path;

    return newOne;
  }

  public @NotNull StringStylist getStylist() {
    return this.stylist;
  }
}
