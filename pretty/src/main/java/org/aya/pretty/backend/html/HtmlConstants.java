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
    HOVER_TYPE_POPUP_STYLE = tag(readResource("aya-html/hover-type-popup.css"), "style");
    HOVER_STYLE = tag(readResource("aya-html/hover.css"), "style");
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

  @Language(value = "JavaScript")
  @NotNull String HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN = """
    var highlight = function (on) {
      return function () {
        var links = document.getElementsByTagName('a');
        for (var i = 0; i < links.length; i++) {
          var that = links[i];
          if (this.href !== that.href) continue;
          if (on) that.classList.add("hover-highlight");
          else that.classList.remove("hover-highlight");
        }
      }
    };
    """;
  @Language(value = "JavaScript")
  @NotNull String HOVER_ALL_OCCURS_JS_INIT = """
    var links = document.getElementsByTagName('a');
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      if (!link.hasAttribute("href")) continue;
      link.onmouseover = highlight(true);
      link.onmouseout = highlight(false);
    }
    """;

  /** see: <a href="https://github.com/plt-amy/1lab/blob/5e5a22abce8a5cfb62b5f815e1231c1e34bb0a12/support/web/js/highlight-hover.ts#L22">1lab</a> */
  @Language(value = "JavaScript")
  @NotNull String HOVER_SHOW_TOOLTIP_JS_SHOW_FN = """
    var currentHover = null;
    var showTooltip = function (on) {
      return function () {
        var link = this;
        const text = link.getAttribute("data-tooltip-text");
        if (text) {
          if (currentHover) {
            currentHover.remove();
            currentHover = null;
          }
        
          if (on) {
            currentHover = document.createElement("div");
            currentHover.innerHTML = atob(text);
            currentHover.classList.add("AyaTooltipPopup");
            document.body.appendChild(currentHover);
        
            const selfRect = link.getBoundingClientRect();
            const hoverRect = currentHover.getBoundingClientRect();
            // If we're close to the bottom of the page, push the tooltip above instead.
            // The constant here is arbitrary, because trying to convert em to px in JS is a fool's errand.
            if (selfRect.bottom + hoverRect.height + 30 > window.innerHeight) {
              // 2em from the material mixin. I'm sorry
              currentHover.style.top = `calc(${link.offsetTop - hoverRect.height + 5}px - 2em)`;
            } else {
              currentHover.style.top = `${link.offsetTop + link.offsetHeight + 5}px`;
            }
            currentHover.style.left = `${link.offsetLeft}px`;
          }
        }
      }
    };
    """;
  @Language(value = "JavaScript")
  @NotNull String HOVER_SHOW_TOOLTIP_JS_INIT = """
    var links = document.getElementsByClassName('aya-tooltip');
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      if (!link.hasAttribute("data-tooltip-text")) continue;
      link.onmouseover = showTooltip(true);
      link.onmouseout = showTooltip(false);
    }
    """;

  @SuppressWarnings("LanguageMismatch")
  @Language(value = "HTML")
  @NotNull String HOVER = """
    <script>
    """ + HOVER_ALL_OCCURS_JS_HIGHLIGHT_FN + HOVER_SHOW_TOOLTIP_JS_SHOW_FN + """
    window.onload = function () {
    """ + HOVER_ALL_OCCURS_JS_INIT + HOVER_SHOW_TOOLTIP_JS_INIT + """
    };
    </script>
    """;
  @SuppressWarnings("LanguageMismatch")
  @Language(value = "HTML")
  @NotNull String HOVER_SSR = """
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
  @NotNull String KATEX_AUTO_RENDER_EXTERNAL_RESOURCES = """
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.4/dist/katex.min.css" integrity="sha384-vKruj+a13U8yHIkAyGgK1J3ArTLzrFGBbBc0tDp4ad/EyewESeXE/Iv67Aj8gKZ0" crossorigin="anonymous">
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.4/dist/katex.min.js" integrity="sha384-PwRUT/YqbnEjkZO0zZxNqcxACrXe+j766U2amXcgMg5457rve2Y7I6ZJSm2A0mS4" crossorigin="anonymous"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.4/dist/contrib/auto-render.min.js" integrity="sha384-+VBxd3r6XgURycqtZ117nYw44OOcIax56Z4dCRWbxyPt0Koah1uHoK0o4+/RRE05" crossorigin="anonymous"></script>
    """;
  @Language(value = "JavaScript")
  @NotNull String KATEX_AUTO_RENDER_INIT = """
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
  @SuppressWarnings("LanguageMismatch")
  @Language(value = "HTML")
  @NotNull String KATEX_AUTO_RENDER =
    KATEX_AUTO_RENDER_EXTERNAL_RESOURCES + """
      <script>
      """ + KATEX_AUTO_RENDER_INIT + """
      </script>
      """;
}
