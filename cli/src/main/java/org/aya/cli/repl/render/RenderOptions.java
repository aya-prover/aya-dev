// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.render;

import com.google.gson.*;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.pretty.printer.ColorScheme;
import org.aya.pretty.printer.StyleFamily;
import org.aya.pretty.style.AyaColorScheme;
import org.aya.pretty.style.AyaStyleFamily;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

public record RenderOptions(
  @NotNull ColorScheme colorScheme,
  @NotNull StyleFamily styleFamily
) {
  public final static RenderOptions CLI_DEFAULT = new RenderOptions(
    AyaColorScheme.EMACS,
    AyaStyleFamily.ADAPTIVE_CLI
  );

  public final static class Adapter implements JsonDeserializer<RenderOptions>, JsonSerializer<RenderOptions> {

    public @NotNull Reporter reporter;
    public @NotNull RenderOptions fallback;

    public Adapter(@NotNull Reporter reporter, @NotNull RenderOptions fallback) {
      this.reporter = reporter;
      this.fallback = fallback;
    }

    /// region deserialize ColorScheme

    public @NotNull ColorScheme deserializeColorScheme(@NotNull ColorSchemeObject options) {
      var builder = MutableMap.<String, @NotNull Integer>create();

      putOrFallback(builder, options.keyword, AyaColorScheme.Key.Keyword);
      putOrFallback(builder, options.fnName, AyaColorScheme.Key.FnCall);
      putOrFallback(builder, options.generalized, AyaColorScheme.Key.Generalized);
      putOrFallback(builder, options.dataName, AyaColorScheme.Key.DataCall);
      putOrFallback(builder, options.structName, AyaColorScheme.Key.StructCall);
      putOrFallback(builder, options.conName, AyaColorScheme.Key.ConCall);
      putOrFallback(builder, options.fieldName, AyaColorScheme.Key.FieldCall);

      return new AyaColorScheme(builder);
    }

    public void putOrFallback(@NotNull MutableMap<String, @NotNull Integer> map, @Nullable Color color, @NotNull AyaColorScheme.Key key) {
      var realColor = color == null
        ? fallback.colorScheme().definedColors().getOption(key.key())
        : Option.some(color.color);

      // avoid put null
      if (realColor.isDefined()) {
        map.put(key.key(), realColor.get());
      }
    }

    /// endregion

    /// region serialize ColorScheme

    public @NotNull ColorSchemeObject serializeColorScheme(@NotNull ColorScheme colorScheme) {
      var builder = new ColorSchemeObject();

      builder.keyword = getColorOrNull(colorScheme, AyaColorScheme.Key.Keyword);
      builder.fnName = getColorOrNull(colorScheme, AyaColorScheme.Key.FnCall);
      builder.generalized = getColorOrNull(colorScheme, AyaColorScheme.Key.Generalized);
      builder.dataName = getColorOrNull(colorScheme, AyaColorScheme.Key.DataCall);
      builder.structName = getColorOrNull(colorScheme, AyaColorScheme.Key.StructCall);
      builder.conName = getColorOrNull(colorScheme, AyaColorScheme.Key.ConCall);
      builder.fieldName = getColorOrNull(colorScheme, AyaColorScheme.Key.FieldCall);

      return builder;
    }

    public @Nullable Color getColorOrNull(@NotNull ColorScheme colorScheme, @NotNull AyaColorScheme.Key key) {
      return colorScheme.definedColors().getOption(key.key()).map(Color::new).getOrNull();
    }

    /// endregion

    /// region deserialize StyleFamily

    private final static String KEY_STYLE_FAMILY_DEFAULT = "default";
    private final static String KEY_STYLE_FAMILY_CLI = "cli";

    public final static ImmutableMap<String, StyleFamily> BUILTIN_STYLE_FAMILY = ImmutableMap.of(
      KEY_STYLE_FAMILY_DEFAULT, AyaStyleFamily.DEFAULT,
      KEY_STYLE_FAMILY_CLI, AyaStyleFamily.ADAPTIVE_CLI
    );

    public @NotNull StyleFamily styleFamilyFromJson(@NotNull String name) {
      var styleFamily = BUILTIN_STYLE_FAMILY.getOption(name);

      if (styleFamily.isEmpty()) {
        reporter.reportString("Invalid style family: '" + name + "'");
        return fallback.styleFamily();
      }

      return styleFamily.get();
    }

    /// endregion

    /// region serialize StyleFamily

    public @Nullable String serializeStyleFamily(@NotNull StyleFamily styleFamily) {
      return BUILTIN_STYLE_FAMILY
        .toImmutableSeq()
        .firstOption(x -> x._2 == styleFamily)
        .map(x -> x._1)
        .getOrNull();
    }

    /// endregion

    @Override
    public RenderOptions deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      assert json != null;
      assert context != null;

      var jsonObj = (RenderOptionsObject) context.deserialize(json, RenderOptionsObject.class);

      ColorScheme colorScheme;
      StyleFamily styleFamily;

      if (jsonObj.colorScheme == null) {
        reporter.reportString("'colorScheme' is null");
        colorScheme = fallback.colorScheme();
      } else {
        colorScheme = deserializeColorScheme(jsonObj.colorScheme);
      }

      if (jsonObj.styleFamily == null) {
        reporter.reportString("'styleFamily' is null");
        styleFamily = fallback.styleFamily();
      } else {
        styleFamily = styleFamilyFromJson(jsonObj.styleFamily);
      }

      return new RenderOptions(colorScheme, styleFamily);
    }

    @Override
    public JsonElement serialize(RenderOptions src, Type typeOfSrc, JsonSerializationContext context) {
      assert src != null;
      assert context != null;

      var object = new RenderOptionsObject();

      object.colorScheme = serializeColorScheme(src.colorScheme());
      object.styleFamily = serializeStyleFamily(src.styleFamily());

      return context.serialize(object, RenderOptionsObject.class);
    }
  }
}
