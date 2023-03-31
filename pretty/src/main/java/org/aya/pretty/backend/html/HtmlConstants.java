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
    HOVER_TYPE_POPUP_STYLE = tag("style", readResource("aya-html/hover-type-popup.css"));
    HOVER_STYLE = tag("style", readResource("aya-html/hover.css"));
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
    return "<" + tag + ">" + read + "</" + tag + ">";
  }

  public static final @NotNull String HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN;
  public static final @NotNull String HOVER_ALL_OCCURS_JS_INIT;
  public static final @NotNull String HOVER_SHOW_TOOLTIP_JS_SHOW_FN;
  public static final @NotNull String HOVER_SHOW_TOOLTIP_JS_INIT;

  static {
    HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN = readResource("aya-html/highlight-fn.js");
    HOVER_SHOW_TOOLTIP_JS_INIT = readResource("aya-html/show-tooltip.js");
    HOVER_SHOW_TOOLTIP_JS_SHOW_FN = readResource("aya-html/show-tooltip-show.js");
    HOVER_ALL_OCCURS_JS_INIT = readResource("aya-html/highlight-occurrences.js");
  }

  @Language(value = "HTML")
  public static final @NotNull String HOVER = """
    <script>
    """ + HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN + HOVER_SHOW_TOOLTIP_JS_SHOW_FN + """
    window.onload = function () {
    """ + HOVER_ALL_OCCURS_JS_INIT + HOVER_SHOW_TOOLTIP_JS_INIT + """
    };
    </script>
    """;
  @Language(value = "HTML")
  public static final @NotNull String HOVER_SSR = """
    <script>
    export default {
      mounted() {
    """ + HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN + HOVER_SHOW_TOOLTIP_JS_SHOW_FN + """
    """ + HOVER_ALL_OCCURS_JS_INIT + HOVER_SHOW_TOOLTIP_JS_INIT + """
      }
    }
    </script>
    """;

  /** <a href="https://katex.org/docs/autorender.html">Auto Render</a> */
  @Language(value = "HTML")
  public static final @NotNull String KATEX_AUTO_RENDER_EXTERNAL_RESOURCES = """
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.4/dist/katex.min.css" integrity="sha384-vKruj+a13U8yHIkAyGgK1J3ArTLzrFGBbBc0tDp4ad/EyewESeXE/Iv67Aj8gKZ0" crossorigin="anonymous">
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.4/dist/katex.min.js" integrity="sha384-PwRUT/YqbnEjkZO0zZxNqcxACrXe+j766U2amXcgMg5457rve2Y7I6ZJSm2A0mS4" crossorigin="anonymous"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.4/dist/contrib/auto-render.min.js" integrity="sha384-+VBxd3r6XgURycqtZ117nYw44OOcIax56Z4dCRWbxyPt0Koah1uHoK0o4+/RRE05" crossorigin="anonymous"></script>
    """;
  @Language(value = "JavaScript")
  public static final @NotNull String KATEX_AUTO_RENDER_INIT = """
    document.addEventListener("DOMContentLoaded", function() {
        var blocks = document.getElementsByClassName('doc-katex-input');
        for (var i = 0; i < blocks.length; i++) {
          var block = blocks[i];
          renderMathInElement(block, {
            throwOnError : false
          });
        }
    });
    """;
  @Language(value = "HTML")
  public static final @NotNull String KATEX_AUTO_RENDER = KATEX_AUTO_RENDER_EXTERNAL_RESOURCES + tag("script", KATEX_AUTO_RENDER_INIT);
}
