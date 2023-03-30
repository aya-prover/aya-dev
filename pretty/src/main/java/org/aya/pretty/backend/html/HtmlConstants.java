// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.backend.html;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public interface HtmlConstants {
  @Language(value = "HTML")
  @NotNull String HOVER_POPUP_STYLE = """
    <style>
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
      background-color: rgba(18,26,44,0.8);
      color: #fff;
      box-shadow: 1px 1px 14px rgba(0,0,0,0.1)
    }
    .Aya .aya-hover:hover:after {
      /* show on hover */
      transform: translate(0px, -110%);
      visibility: visible;
      display: block;
    }
    </style>
    """;
  @Language(value = "HTML")
  @NotNull String HOVER_STYLE = """
    <style>
    .Aya a { text-decoration: none; }
    .Aya a:hover { text-decoration: none; }
    .Aya a[href]:hover { background-color: #B4EEB4; }
    .Aya [href].hover-highlight { background-color: #B4EEB4; }
    </style>
    """;
  @Language(value = "JavaScript")
  @NotNull String HOVER_HIGHLIGHT_ALL_OCCURS_JS_HIGHLIGHT_FN = """
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
  @NotNull String HOVER_HIGHLIGHT_ALL_OCCURS_JS_INIT = """
    var links = document.getElementsByTagName('a');
    for (var i = 0; i < links.length; i++) {
      var link = links[i];
      if (!link.hasAttribute("href")) continue;
      link.onmouseover = highlight(true);
      link.onmouseout = highlight(false);
    }
    """;
  @SuppressWarnings("LanguageMismatch")
  @Language(value = "HTML")
  @NotNull String HOVER_ALL_OCCURS = """
    <script>
    """ + HOVER_HIGHLIGHT_ALL_OCCURS_JS_HIGHLIGHT_FN + """
    window.onload = function () {
    """ + HOVER_HIGHLIGHT_ALL_OCCURS_JS_INIT + """
    };
    </script>
    """;
  @SuppressWarnings("LanguageMismatch")
  @Language(value = "HTML")
  @NotNull String HOVER_ALL_OCCURS_SSR = """
    <script>
    export default {
      mounted() {
    """ + HOVER_HIGHLIGHT_ALL_OCCURS_JS_HIGHLIGHT_FN + """
    """ + HOVER_HIGHLIGHT_ALL_OCCURS_JS_INIT + """
      }
    }
    </script>
    """;
}
