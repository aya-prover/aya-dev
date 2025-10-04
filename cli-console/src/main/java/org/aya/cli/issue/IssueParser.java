// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.issue;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import org.aya.generic.Constants;
import org.aya.literate.Literate;
import org.aya.literate.parser.BaseMdParser;
import org.aya.literate.parser.InterestingLanguage;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public record IssueParser(@NotNull SourceFile issueFile, @NotNull Reporter reporter) {
  /// @param name with postfix, in fact, this can be a path, such as `bar/foo.aya`
  public record File(@Nullable String name, @NotNull String content) { }
  public record Version(int major, int minor, boolean snapshot, @Nullable String hash) {
    public @NotNull String versionNumber() {
      return major + "." + minor;
    }

    @Override
    public @NotNull String toString() {
      return versionNumber() + (snapshot ? "-SNAPSHOT" : "") + (hash == null ? "" : " (" + hash + ")");
    }
  }

  public record ParseResult(@NotNull ImmutableSeq<File> files, @Nullable Version ayaVersion) { }

  public enum BlockType implements BlockParser.Kind {
    VERSION, FILES
  }

  public static final @NotNull Pattern VERSION_PATTERN = Pattern.compile("(\\d+).(\\d+)(-SNAPSHOT)?( \\([a-z\\d]{16}\\))?");

  // TODO: less functional, use mutability
  // TODO: not sure if we really need to parse markdown

  /// @return null if issue tracker is not enabled
  public @Nullable ParseResult parse() {
    var isEnabled = issueFile.sourceCode().stripLeading().startsWith("<!-- ISSUE TRACKER ENABLE -->");
    if (!isEnabled) return null;

    var blocks = BlockParser.parse(issueFile, reporter, s -> {
      try {
        return BlockType.valueOf(s);
      } catch (IllegalArgumentException _) {
        return null;
      }
    });

    var version = blocks.findFirst(it -> it.blockType() == BlockType.VERSION)
      .flatMap(it -> Option.ofNullable(parseAyaVersion(it.content().toString())))
      .getOrNull();

    // no thank you
    var files = blocks.filter(it -> it.blockType() == BlockType.FILES);

    var ayaFiles = files.flatMap(b -> {
      var mdParser = new BaseMdParser(
        new SourceFile(issueFile.display(), Option.none(), b.content().toString()),
        reporter,
        ImmutableSeq.of(InterestingLanguage.ALL)
      );

      var literate = mdParser.parseLiterate();
      var seq = literate instanceof Literate.Many many ? many.children() : ImmutableSeq.of(literate);
      return parseFiles(seq.view());
    });

    return new ParseResult(ayaFiles, version);
  }

  private static @NotNull ImmutableSeq<File> parseFiles(@NotNull SeqView<Literate> seq) {
    var remains = skipEmpty(seq);
    var files = MutableList.<File>create();
    String fileName = null;

    while (remains.isNotEmpty()) {
      var unit = remains.getFirst();

      switch (unit) {
        case Literate.Many many -> {
          var inlineCodes = many.children().filterIsInstance(Literate.InlineCode.class);
          if (inlineCodes.size() == 1) {
            var maybeFileName = inlineCodes.getFirst();
            var name = parseFileName(maybeFileName.code);
            if (name != null) {
              fileName = name;
            }
          }

          // keep old, unused file name
        }
        case Literate.CodeBlock block -> {
          // TODO: maybe only aya code, by changing [InterestingLanguage] of parser.
          var code = block.code;
          if (code.startsWith("//")) {
            var firstLineBreak = code.indexOf('\n');
            if (firstLineBreak == -1) firstLineBreak = code.length();
            var firstLine = code.substring(0, firstLineBreak);
            var name = parseFileName(firstLine);
            if (name != null) fileName = name;
          }

          files.append(new File(fileName, code));
          fileName = null;
        }
        default -> { }
      }

      remains = skipEmpty(remains.drop(1));
    }

    return files.toSeq();
  }

  private static @Nullable String parseFileName(@NotNull String text) {
    var postfixIdx = text.indexOf(Constants.AYA_POSTFIX);
    if (postfixIdx != -1) {
      var lastLetter = text.lastIndexOf(' ', postfixIdx) + 1;
      var fileName = text.substring(lastLetter, postfixIdx + Constants.AYA_POSTFIX.length());
      if (fileName.isEmpty()) return null;
      return fileName;
    } else return null;
  }

  private static @Nullable Version parseAyaVersion(@NotNull String content) {
    var matcher = VERSION_PATTERN.matcher(content);
    if (matcher.find()) {
      var major = Integer.parseInt(matcher.group(1));
      var minor = Integer.parseInt(matcher.group(2));
      var isSnapshot = matcher.group(3) != null;
      var commit = matcher.group(4);

      return new Version(major, minor, isSnapshot, commit);
    }

    return null;
  }

  private static @NotNull SeqView<Literate> skipEmpty(@NotNull SeqView<Literate> seq) {
    // TODO: handle empty space case
    return seq.dropWhile(it -> it == Literate.EOL);
  }
}
