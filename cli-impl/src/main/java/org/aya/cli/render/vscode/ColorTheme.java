// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.render.vscode;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import kala.collection.Map;
import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import kala.control.Option;
import kala.control.Try;
import kala.value.LazyValue;
import org.aya.cli.render.Color;
import org.aya.cli.render.adapter.EitherAdapter;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ColorTheme {
  public static Try<ColorTheme> loadFrom(@NotNull Path path) throws JsonParseException {
    return Try.of(() -> {
      var jsonObj = newGsonBuilder()
        .setLenient()
        .create()
        .fromJson(Files.newBufferedReader(path), VscColorTheme.class);
      var include = jsonObj.include;
      var colorTheme = jsonObj.buildMap();
      Path includePath = null;

      if (include != null) {
        includePath = Paths.get(include);
        if (!includePath.isAbsolute()) {
          includePath = path.resolveSibling(includePath);
        }
      }

      return new ColorTheme(includePath, colorTheme);
    });
  }

  public static @NotNull GsonBuilder newGsonBuilder() {
    return new GsonBuilder()
      .registerTypeAdapter(Color.class, new Color.Adapter())
      .registerTypeAdapter(Either.class, new EitherAdapter());
  }

  public final @Nullable Path include;
  public final @NotNull ImmutableMap<String, VscColorTheme.TokenColor.Settings> colorTheme;
  public final Option<LazyValue<Try<ColorTheme>>> parent;

  /**
   * @param include an absolute path
   */
  public ColorTheme(@Nullable Path include, @NotNull ImmutableMap<String, VscColorTheme.TokenColor.Settings> colorTheme) {
    this.include = include;
    this.colorTheme = colorTheme;

    if (include != null) {
      // TODO: stack overflow if self-include appears.
      parent = Option.some(LazyValue.of(() -> loadFrom(include)));
    } else {
      parent = Option.none();
    }
  }

  public Option<VscColorTheme.TokenColor.Settings> find(@NotNull String scope) {
    var settings = this.colorTheme.getOption(scope);

    if (settings.isEmpty() && parent.isDefined()) {
      var parent = this.parent.get();
      return parent.get()
        .toResult().toOption()
        .flatMap(x -> x.find(scope));
    }

    return settings;
  }

  public @NotNull ColorScheme buildColorScheme(@Nullable ColorScheme fallback) {
    var builder = MutableMap.<String, Integer>create();
    var fallbackColors = fallback == null
      ? Map.<String, Integer>empty()
      : fallback.definedColors();

    findAndPut(builder, AyaStyleKey.Keyword.key(), VscColorTheme.SCOPE_KEYWORD, fallbackColors);
    findAndPut(builder, AyaStyleKey.Fn.key(), VscColorTheme.SCOPE_FN_CALL, fallbackColors);
    findAndPut(builder, AyaStyleKey.Generalized.key(), VscColorTheme.SCOPE_GENERALIZED, fallbackColors);
    findAndPut(builder, AyaStyleKey.Data.key(), VscColorTheme.SCOPE_DATA_CALL, fallbackColors);
    findAndPut(builder, AyaStyleKey.Clazz.key(), VscColorTheme.SCOPE_STRUCT_CALL, fallbackColors);
    findAndPut(builder, AyaStyleKey.Con.key(), VscColorTheme.SCOPE_CON_CALL, fallbackColors);
    findAndPut(builder, AyaStyleKey.Member.key(), VscColorTheme.SCOPE_FIELD_CALL, fallbackColors);

    return new AyaColorScheme(builder);
  }

  public void findAndPut(@NotNull MutableMap<String, Integer> putTo, @NotNull String key, @NotNull Seq<String> scope, @NotNull Map<String, Integer> fallback) {
    var result = scope.view().map(this::find).findFirst(Option::isDefined);

    if (result.isDefined()) {
      var settings = result.get().get();
      var foreground = settings.foreground;

      if (foreground != null) {
        putTo.put(key, foreground.color);
        return;
      }
    }

    var fallbackValue = fallback.getOption(key);

    if (fallbackValue.isDefined()) {
      putTo.put(key, fallbackValue.get());
    }
  }
}
