// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.issue;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableEnumSet;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.control.OptionContainer;
import kala.control.Result;
import org.aya.literate.Literate;
import org.aya.literate.parser.BaseMdParser;
import org.aya.literate.parser.InterestingLanguage;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.regex.Pattern;

public record IssueParser(@NotNull SourceFile issueFile, @NotNull Reporter reporter) {
  /// @param name with postfix, in fact, this can be a path, such as `bar/foo.aya`
  public record File(@Nullable String name, @NotNull String content) {
    /// @param base the base path
    /// @return a valid path by [#name], null if [#name] is null or [#name] is invalid
    public @Nullable Path getValidFileName(@NotNull Path base) {
      if (name == null) return null;

      var path = Path.of(name).normalize();
      if (path.isAbsolute()) return null;

      base = base.normalize();
      var resolved = base.resolve(path).normalize();
      if (!resolved.startsWith(base)) return null;

      return resolved;
    }
  }
  public record Version(int major, int minor, int patch, boolean snapshot, @Nullable String hash, int java) {
    public Version {
      // java == -1 implies hash not null
      assert java == -1 || hash != null;
    }

    public @NotNull String versionNumber() {
      return major + "." + minor + "." + patch;
    }

    @Override
    public @NotNull String toString() {
      var versionPostfix = java == -1
        ? ""
        : ", jdk " + java;
      var hashAndVersion = hash == null
        ? ""
        : "(" + hash + versionPostfix + ")";

      return versionNumber() + (snapshot ? "-SNAPSHOT" : "") + hashAndVersion;
    }
  }

  public record ParseResult(
    @NotNull MutableEnumSet<Modifiers> modifiers,
    @NotNull ImmutableSeq<File> files,
    @Nullable Version ayaVersion
  ) { }

  public enum BlockType implements BlockParser.Kind {
    VERSION, FILES
  }

  public enum Modifiers {
    // invert the success condition
    INVERTED,
    // always success
    PASS
  }

  // Aya v<MAJOR>.<MINOR>.<PATCH>-SNAPSHOT (<COMMIT HASH>, jdk <JAVA VERSION>)
  public static final @NotNull Pattern VERSION_PATTERN = Pattern.compile("(?:Aya v?)?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(-SNAPSHOT)?(?: \\(([a-z0-9]{40})(?:, jdk (\\d+))?\\))?");
  public static final @NotNull Pattern FILE_PATTERN = Pattern.compile("[a-zA-Z0-9\\-_/]+\\.aya");
  public static final @NotNull Pattern INDICATOR = Pattern.compile("^<!-- ISSUE TRACKER ENABLE (([A-Z]+ )*)-->");

  /// @return null if issue tracker is not enabled
  public @Nullable ParseResult parse() {
    var beginMatcher = INDICATOR.matcher(issueFile.sourceCode().stripLeading());
    var isEnabled = beginMatcher.find();
    if (!isEnabled) return null;

    // modifiers
    var rawModifiers = beginMatcher.group(1);
    var modiList = rawModifiers == null || rawModifiers.isEmpty()
      ? ImmutableSeq.<String>empty()
      : ImmutableSeq.from(rawModifiers.split(" "));

    var parsedModi = modiList.view().<Result<Modifiers, String>>map(it -> {
      try {
        return Result.ok(Modifiers.valueOf(it));
      } catch (IllegalArgumentException _) {
        return Result.err(it);
      }
    });

    // TODO: report unknown modifier, we may need a reporter
    var modifiers = MutableEnumSet.from(Modifiers.class, parsedModi.mapNotNull(OptionContainer::getOrNull));

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

    return new ParseResult(modifiers, ayaFiles, version);
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
    var matcher = FILE_PATTERN.matcher(text);
    if (matcher.find()) {
      return matcher.group(0);
    } else {
      return null;
    }
  }

  private static @Nullable Version parseAyaVersion(@NotNull String content) {
    var matcher = VERSION_PATTERN.matcher(content);
    if (matcher.find()) {
      var major = Integer.parseInt(matcher.group(1));
      var minor = Integer.parseInt(matcher.group(2));
      var rawPatch = matcher.group(3);
      var patch = rawPatch == null ? 0 : Integer.parseInt(rawPatch);
      var isSnapshot = matcher.group(4) != null;
      var commit = matcher.group(5);
      var rawJavaVersion = matcher.group(6);
      var javaVersion = rawJavaVersion == null ? -1 : Integer.parseInt(rawJavaVersion);

      return new Version(major, minor, patch, isSnapshot, commit, javaVersion);
    }

    return null;
  }

  private static @NotNull SeqView<Literate> skipEmpty(@NotNull SeqView<Literate> seq) {
    // TODO: handle empty space case
    return seq.dropWhile(it -> it == Literate.EOL);
  }
}
