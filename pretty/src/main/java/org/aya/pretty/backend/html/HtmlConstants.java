// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public final class HtmlConstants {
  public static final @NotNull String HOVER_TYPE_POPUP_STYLE;
  public static final @NotNull String HOVER_STYLE;

  static {
    HOVER_TYPE_POPUP_STYLE = tag("style", readResource("/aya-html/hover-tooltip.css"));
    HOVER_STYLE = tag("style", readResource("/aya-html/hover.css"));
  }

  private static @NotNull String readResource(String name) {
    try (var stream = HtmlConstants.class.getResourceAsStream(name)) {
      assert stream != null;
      try (var reader = new BufferedReader(new InputStreamReader(stream))) {
        return reader.lines().collect(Collectors.joining("\n"));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static @NotNull String tag(String tag, String read) {
    return "<" + tag + ">\n" + read + "\n</" + tag + ">\n";
  }

  /**
   * wrap JavaScript code segment in a new scope,
   * for possible local variable name clashing.
   */
  private static @NotNull String scoped(String read) {
    return "{\n" + read.indent(2) + "\n}\n";
  }

  public static final @NotNull String HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN;
  public static final @NotNull String HOVER_ALL_OCCURS_JS_INIT;
  public static final @NotNull String HOVER_SHOW_TOOLTIP_JS_SHOW_FN;
  public static final @NotNull String HOVER_SHOW_TOOLTIP_JS_INIT;

  static {
    HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN = readResource("/aya-html/highlight-fn.js");
    HOVER_SHOW_TOOLTIP_JS_INIT = readResource("/aya-html/show-tooltip.js");
    HOVER_SHOW_TOOLTIP_JS_SHOW_FN = readResource("/aya-html/show-tooltip-fn.js");
    HOVER_ALL_OCCURS_JS_INIT = readResource("/aya-html/highlight-occurrences.js");
  }

  @Language(value = "HTML")
  public static final @NotNull String HOVER = """
    <script>
    """ + HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN + HOVER_SHOW_TOOLTIP_JS_SHOW_FN + """
    window.onload = function () {
    """ + scoped(HOVER_ALL_OCCURS_JS_INIT) + scoped(HOVER_SHOW_TOOLTIP_JS_INIT) + """
    };
    </script>
    """;
  @Language(value = "HTML")
  public static final @NotNull String HOVER_SSR = """
    <script>
    export default {
      mounted() {
    """ + HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN + HOVER_SHOW_TOOLTIP_JS_SHOW_FN + """
    """ + scoped(HOVER_ALL_OCCURS_JS_INIT) + scoped(HOVER_SHOW_TOOLTIP_JS_INIT) + """
      }
    }
    </script>
    """;

  public static final @NotNull String KATEX_AUTO_RENDER_EXTERNAL_RESOURCES;
  public static final @NotNull String KATEX_AUTO_RENDER_INIT;

  static {
    KATEX_AUTO_RENDER_EXTERNAL_RESOURCES = readResource("/aya-html/katex.html");
    KATEX_AUTO_RENDER_INIT = readResource("/aya-html/katex-auto-render.js");
  }

  public static final @NotNull String KATEX_AUTO_RENDER = KATEX_AUTO_RENDER_EXTERNAL_RESOURCES + tag("script", KATEX_AUTO_RENDER_INIT);
}
