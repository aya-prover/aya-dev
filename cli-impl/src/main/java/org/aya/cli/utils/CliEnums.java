// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import org.aya.cli.render.RenderOptions;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface CliEnums {
  static @NotNull CliEnums.PrettyFormat detectFormat(@NotNull Path outputFile) {
    var name = outputFile.getFileName().toString();
    if (name.endsWith(".md")) return PrettyFormat.markdown;
    if (name.endsWith(".tex")) return PrettyFormat.latex;
    if (name.endsWith(".katex")) return PrettyFormat.katex;
    if (name.endsWith(".html")) return PrettyFormat.html;
    return PrettyFormat.plain;
  }
  enum PrettyStage {
    raw,
    scoped,
    typed,
    literate,
  }

  enum PrettyFormat {
    html(RenderOptions.OutputTarget.HTML),
    plain(RenderOptions.OutputTarget.Plain),
    latex(RenderOptions.OutputTarget.LaTeX),
    katex(RenderOptions.OutputTarget.KaTeX),
    markdown(RenderOptions.OutputTarget.AyaMd),
    unix(RenderOptions.OutputTarget.Unix),
    ansi16(RenderOptions.OutputTarget.ANSI16);

    public final @NotNull RenderOptions.OutputTarget target;

    PrettyFormat(RenderOptions.@NotNull OutputTarget target) {
      this.target = target;
    }
  }
}
