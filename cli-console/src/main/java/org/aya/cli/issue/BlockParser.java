// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.issue;

import com.intellij.openapi.util.TextRange;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.text.StringSlice;
import org.aya.cli.issue.error.BlockParserProblem;
import org.aya.util.Panic;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Extract block surrounded by `<!-- BEGIN XXX -->` and `<!-- END XXX -->`
public class BlockParser<T extends BlockParser.Kind> {
  public interface Kind { }

  public record Block<T extends Kind>(@NotNull T blockType, @NotNull StringSlice content) { }

  public static final @NotNull Pattern MARKER_PATTERN = Pattern.compile("<!-- (BEGIN|END) ([A-Z ]+) -->");

  public static <T extends Kind> @NotNull ImmutableSeq<Block<T>> parse(
    @NotNull SourceFile source,
    @NotNull Reporter reporter,
    @NotNull Function<String, @Nullable T> kindFactory
  ) {
    var blocks = FreezableMutableList.<Block<T>>create();
    var matcher = MARKER_PATTERN.matcher(source.sourceCode());

    @Nullable T lastBeginMark = null;
    int blockBeginOffset = -1;

    while (matcher.find()) {
      var markerType = matcher.group(1);
      var blockType = matcher.group(2);

      switch (markerType) {
        case "BEGIN" -> {
          if (lastBeginMark != null) {
            // unexpected begin marker
            reporter.report(new BlockParserProblem.UnexpectedMarker(
              of(source, matcher),
              true,
              lastBeginMark.toString(),
              blockType));
            continue;
          } else {
            lastBeginMark = kindFactory.apply(blockType);
            blockBeginOffset = matcher.end();
          }
        }
        case "END" -> {
          var mBlockType = kindFactory.apply(blockType);
          if (lastBeginMark == null) {
            // unexpected end marker
            reporter.report(new BlockParserProblem.UnexpectedMarker(
              of(source, matcher),
              false,
              null, blockType
            ));
            continue;
          } else if (!lastBeginMark.equals(mBlockType)) {
            // block type doesn't match
            reporter.report(new BlockParserProblem.UnexpectedMarker(
              of(source, matcher),
              false,
              lastBeginMark.toString(),
              blockType
            ));
            continue;
          } else {
            // expected end marker, block type matches
            blocks.append(new Block<>(
              lastBeginMark,
              StringSlice.of(source.sourceCode(), blockBeginOffset, matcher.start())
            ));

            // consume
            lastBeginMark = null;
            blockBeginOffset = -1;
          }
        }
        default -> Panic.unreachable();
      }
    }

    if (lastBeginMark != null) {
      // not yet close
      reporter.report(new BlockParserProblem.UnclosedBlock(
        // blockBeginOffset is at the end of the last begin mark, we should use the begin offset of the last begin mark
        SourcePos.of(TextRange.create(blockBeginOffset, source.sourceCode().length()), source, false),
        lastBeginMark.toString()
      ));
    }

    return blocks.freeze();
  }

  public static @NotNull SourcePos of(@NotNull SourceFile file, @NotNull Matcher matcher) {
    return SourcePos.of(TextRange.create(matcher.start(), matcher.end()), file, true);
  }
}
