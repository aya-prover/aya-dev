// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.issue;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.generic.Constants;
import org.aya.literate.Literate;
import org.aya.pretty.doc.Doc;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public class IssueParser {
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

  public enum BlockType implements BlockParser.Kind {
    VERSION, FILES
  }

  public final @NotNull Pattern VERSION_PATTERN = Pattern.compile("((\\d+.\\d+)(-SNAPSHOT)?)( \\([a-z\\d]{16}\\))?");
  public final @NotNull String MAGIC_ENABLE = "ISSUE CHECKER ENABLE";
  public final @NotNull String MAGIC_BEGIN_FILES = "BEGIN FILES";
  public final @NotNull String MAGIC_END_FILES = "END FILES";
  public final @NotNull String MAGIC_AYA_VERSION = "AYA VERSION";
  public final @NotNull String MAGIC_BEGIN_VERSION = "BEGIN VERSION";
  public final @NotNull String MAGIC_END_VERSION = "END VERSION";
  public final @NotNull ImmutableSeq<String> MAGIC_BEGIN = ImmutableSeq.of(MAGIC_BEGIN_FILES, MAGIC_AYA_VERSION);

  // TODO: less functional, use mutability
  // TODO: not sure if we really need to parse markdown

  public @Nullable Tuple2<ImmutableSeq<File>, @Nullable Version> accept(@NotNull Literate issue) {
    if (!(issue instanceof Literate.Many many)) return null;
    var iter = many.children().view();
    var files = MutableList.<File>create();
    Version version = null;

    iter = findPin(iter, ImmutableSeq.of(MAGIC_ENABLE));
    if (iter.isEmpty()) return null;
    iter = iter.drop(1);

    while (true) {
      iter = findPin(iter, MAGIC_BEGIN);

      var magic = (Literate.Comment) iter.getFirstOrNull();
      if (magic == null) break;
      var pin = magic.comment().trim();
      if (pin.equals(MAGIC_BEGIN_FILES)) {
        var result = parseFiles(iter.drop(1));
        files.appendAll(result.component1());
        iter = result.component2();
      } else if (pin.equals(MAGIC_AYA_VERSION)) {
        var result = parseAyaVersion(iter.drop(1));
        // we only have one AYA VERSION i guess
        version = result.component1();
        iter = result.component2();
      } else {
        Panic.unreachable();
      }
    }

    return Tuple.of(files.toSeq(), version);
  }

  private @NotNull Tuple2<ImmutableSeq<File>, SeqView<Literate>> parseFiles(@NotNull SeqView<Literate> seq) {
    var remains = skipEmpty(seq);
    var files = MutableList.<File>create();
    var tsuzuku = true;
    String fileName = null;

    while (tsuzuku) {
      var fileLine = remains.getFirstOrNull();
      if (fileLine == null) break;

      switch (fileLine) {
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
            var firstLine = code.substring(0, code.indexOf('\n'));
            var name = parseFileName(firstLine);
            if (name != null) fileName = name;
          }

          files.append(new File(fileName, code));
          fileName = null;
        }
        case Literate.Comment(var comment) when comment.trim().equals(MAGIC_END_FILES) -> tsuzuku = false;
        default -> { }
      }

      remains = remains.drop(1);
    }

    return Tuple.of(files.toSeq(), remains);
  }

  private @Nullable String parseFileName(@NotNull String text) {
    var postfixIdx = text.indexOf(Constants.AYA_POSTFIX);
    if (postfixIdx != -1) {
      var lastLetter = text.lastIndexOf(' ', postfixIdx) + 1;
      var fileName = text.substring(lastLetter, postfixIdx + Constants.AYA_POSTFIX.length());
      if (fileName.isEmpty()) return null;
      return fileName;
    } else return null;
  }

  private @NotNull Tuple2<@Nullable Version, SeqView<Literate>> parseAyaVersion(@NotNull SeqView<Literate> seq) {
    var remains = skipEmpty(seq);
    var first = remains.getFirstOrNull();
    if (!(first instanceof Literate.Many paragraph)) return Tuple.of(null, remains);

    var versionText = paragraph.children().view()
      .dropWhile(it ->
        (!(it.toDoc() instanceof Doc.PlainText(String text)))
          || !text.trim().toLowerCase().startsWith("aya"))
      .takeWhile(it -> it != Literate.EOL).toSeq();

    var text = new Literate.Many(null, versionText).toDoc().commonRender();
    var matcher = VERSION_PATTERN.matcher(text);
    if (!matcher.find()) return Tuple.of(null, remains);

    var version = matcher.group(2);
    var snapshot = matcher.group(3) != null;
    var hash = matcher.group(4);

    var parts = version.split("\\.");
    assert parts.length == 2;
    var major = Integer.parseInt(parts[0]);
    var minor = Integer.parseInt(parts[1]);

    return Tuple.of(new Version(major, minor, snapshot, hash), remains.drop(1));
  }

  private @NotNull SeqView<Literate> findPin(@NotNull SeqView<Literate> literate, @NotNull ImmutableSeq<String> pins) {
    return literate.dropWhile(it -> !(it instanceof Literate.Comment(var comment)) || !pins.contains(comment.trim()));
  }

  private @NotNull SeqView<Literate> skipEmpty(@NotNull SeqView<Literate> seq) {
    // TODO: handle empty space case
    return seq.dropWhile(it -> it == Literate.EOL);
  }
}
