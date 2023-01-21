// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.plct;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.cli.console.MainArgs;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class PLCTReport {
  @NotNull
  public static final ImmutableSeq<Tuple2<String, String>> REPO = ImmutableSeq.of(
    Tuple.of("Aya Prover", "aya-prover/aya-dev"),
    Tuple.of("Aya VSCode", "aya-prover/aya-vscode"),
    Tuple.of("Aya Intellij Plugin", "aya-prover/intellij-aya")
  );
  public static final @NotNull @Nls String SHRUG = "\ud83e\udd37";
  private final @NotNull HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

  private Doc pullRequest(GsonClasses.PR i) {
    return Doc.sepNonEmpty(
      Doc.symbol("+"),
      i.milestone != null ? link(i.milestone.title, i.milestone.url) : Doc.empty(),
      Doc.english(i.title),
      link("PR-%d".formatted(i.number), i.url),
      Doc.english("opened by"),
      link(i.user.login, i.user.url)
    );
  }

  @NotNull private static Doc link(String text, String addr) {
    return Doc.cat(
      Doc.wrap("[", "]", Doc.plain(text)),
      Doc.parened(Doc.plain(addr))
    );
  }

  public int run(@NotNull MainArgs.PlctAction args) throws Exception {
    if (!args.plctReport) {
      System.out.println(SHRUG);
      return 1;
    }
    Doc markdown;
    LocalDateTime since;
    if (args.reportSince > 0) {
      since = LocalDate.now().minusDays(args.reportSince).atStartOfDay();
    } else if (args.reportSince < 0) {
      System.out.println(SHRUG);
      return 1;
    } else since = sinceDate().atStartOfDay();
    if (args.repoName != null) {
      markdown = generate("Your Awesome Repository", args.repoName, since);
    } else {
      markdown = Doc.vcat(REPO
        .mapChecked(t -> generate(t.component1(), t.component2(), since))
        .view()
        .prepended(Doc.plain("## The Aya Theorem Prover")));
    }
    System.out.println(markdown.debugRender());
    return 0;
  }

  public @NotNull Doc generate(@NotNull String name, @NotNull String repo, LocalDateTime since) throws IOException, InterruptedException {
    var req = HttpRequest.newBuilder().GET()
      .uri(URI.create("https://api.github.com/repos/" + repo + "/pulls?state=closed&sort=updated&direction=desc&per_page=100"))
      .build();

    var seq = parse(client.send(req, HttpResponse.BodyHandlers.ofInputStream()).body())
      .view()
      .filter(i -> i.updatedAt.isAfter(since))
      .toImmutableSeq();
    if (seq.isEmpty()) return Doc.empty();
    return Doc.vcat(seq.view()
      .map(this::pullRequest)
      .prepended(Doc.empty())
      .prepended(Doc.cat(
        Doc.plain("[Watch %s]".formatted(name)),
        Doc.parened(Doc.plain("https://github.com/%s".formatted(repo)))
      ))
      .prepended(Doc.empty()));
  }

  public static @NotNull LocalDate sinceDate() {
    var now = LocalDate.now();
    var year = now.getYear();
    var month = now.getMonthValue();
    return now.getDayOfMonth() > 25
      // Generating the report at the end of the month -- collect current month
      ? LocalDate.of(year, month, 1)
      // Generating the report at the start of next month -- collect last month
      : month == 1 ? LocalDate.of(year - 1, 12, 1)
      : LocalDate.of(year, month - 1, 1);
  }

  public static @NotNull ImmutableSeq<GsonClasses.PR> parse(@NotNull InputStream input) {
    return ImmutableSeq.from(new GsonBuilder()
      .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) (json, type, ctx) ->
        LocalDateTime.parse(json.getAsJsonPrimitive().getAsString().replace("Z", "")))
      .create()
      .fromJson(new InputStreamReader(input), GsonClasses.PR[].class));
  }
}
