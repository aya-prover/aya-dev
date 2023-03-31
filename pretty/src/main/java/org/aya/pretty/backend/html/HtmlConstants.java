// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public interface HtmlConstants {
  @Language(value = "HTML")
  @NotNull String HOVER_TYPE_POPUP_STYLE = """
    <style>
    /* for `Doc.HyperLinked`, which is used to show the type of a term on hover. */
    /* Implemented without JavaScript, so it can be rendered correctly in more places. */
    .Aya .aya-hover {
      /* make absolute position available for hover popup */
      position: relative;
      cursor: pointer;
    }
    .Aya [aya-type]:after {
      /* hover text */
      content: attr(aya-type);
      visibility: hidden;
      /* above the text, aligned to left */
      position: absolute;
      top: 0;
      left: 0; /* 0% for left-aligned, 100% for right-aligned*/
      transform: translate(0px, -110%);
      /* spacing */
      white-space: pre;
      padding: 5px 10px;
      background-color: rgba(18, 26, 44, 0.8);
      color: #fff;
      box-shadow: 1px 1px 14px rgba(0, 0, 0, 0.1)
    }
    .Aya .aya-hover:hover:after {
      /* show on hover */
      transform: translate(0px, -110%);
      visibility: visible;
      display: block;
    }
    /* for `Doc.Tooltip`, which is usually used for error messages. */
    .AyaTooltipPopup {
      /* below the text */
      position: absolute;
      z-index: 100;
      /* font style */
      font-size: 0.85em;
      /* spacing */
      padding: 5px 10px;
      background-color: rgba(18, 26, 44, 1.0);
      color: #fff;
      box-shadow: 1px 1px 14px rgba(0, 0, 0, 0.1)
    }
    </style>
    """;
  @Language(value = "HTML")
  @NotNull String HOVER_STYLE = """
    <style>
    .Aya a {
      text-decoration-line: none;
      text-decoration-color: inherit;
      text-underline-position: inherit;
    }
    .Aya a:hover {
      text-decoration-line: none;
      text-decoration-color: inherit;
      text-underline-position: inherit;
    }
    .Aya a[href]:hover { background-color: #B4EEB4; }
    .Aya [href].hover-highlight { background-color: #B4EEB4; }
    </style>
    """;

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

  /** see: <a href="https://github.com/plt-amy/1lab/blob/5e5a22abce8a5cfb62b5f815e1231c1e34bb0a12/support/web/js/highlight-hover.ts#L22">1lab</a>*/
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
