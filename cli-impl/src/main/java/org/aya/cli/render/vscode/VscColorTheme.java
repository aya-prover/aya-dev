// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.render.vscode;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.MutableMap;
import kala.control.Either;
import org.aya.cli.render.Color;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class VscColorTheme {
  public static class TokenColor {
    public static class Settings {
      public @Nullable String fontStyle;
      public @Nullable Color foreground;
      public boolean bold;
      public boolean italic;
      public boolean underline;
    }

    public @Nullable Either<String, List<String>> scope;
    public @Nullable Settings settings;
  }

  public @Nullable String name;
  public @Nullable String include;
  public @Nullable Map<String, Color> colors;
  public @Nullable List<TokenColor> tokenColors;

  // fallback strategy
  public final static Seq<String> SCOPE_KEYWORD = Seq.of("keyword");
  public final static Seq<String> SCOPE_FN_CALL = Seq.of("function", "entity.name.function");
  public final static Seq<String> SCOPE_GENERALIZED = Seq.of("type", "entity.name.type");
  public final static Seq<String> SCOPE_DATA_CALL = Seq.of("class", "entity.name.class", "entity.name.type.class", "entity.name.type");
  public final static Seq<String> SCOPE_STRUCT_CALL = Seq.of("struct", "entity.name.struct", "entity.name.type.struct", "entity.name.type");
  public final static Seq<String> SCOPE_CON_CALL = Seq.of("member", "entity.name.function.member", "entity.name.function");
  public final static Seq<String> SCOPE_FIELD_CALL = Seq.of("property", "variable.other.property", "entity.name.function");

  public ImmutableMap<String, TokenColor.Settings> buildMap() {
    var builder = MutableMap.<String, TokenColor.Settings>create();

    // import simple colors
    if (colors != null) {
      for (var entry : colors.entrySet()) {
        var settings = new TokenColor.Settings();
        settings.foreground = entry.getValue();

        builder.put(entry.getKey(), settings);
      }
    }

    // import token colors
    if (tokenColors != null) {
      for (var entry : tokenColors) {
        var scopes = entry.scope;
        var settings = entry.settings;

        if (scopes != null && settings != null) {
          scopes.fold(List::of, Function.identity()).forEach(scope -> {
            if (scope != null) {
              builder.put(scope, settings);
            }
          });
        }
      }
    }

    return ImmutableMap.from(builder);
  }
}
