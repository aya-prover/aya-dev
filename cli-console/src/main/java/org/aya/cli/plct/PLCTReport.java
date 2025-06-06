// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.plct;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.console.MainArgs;
import org.aya.pretty.doc.Doc;
import org.aya.repl.ReplUtil;
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
import java.util.function.Consumer;

public final class PLCTReport {
  public record Repo(String name, String route) { }
  @NotNull
  public static final ImmutableSeq<Repo> REPO = ImmutableSeq.of(
    new Repo("Aya Prover", "aya-prover/aya-dev"),
    new Repo("Aya Website", "aya-prover/aya-prover-docs"),
    new Repo("Aya VSCode", "aya-prover/aya-vscode"),
    new Repo("Aya Intellij Plugin", "aya-prover/intellij-aya")
  );
  public static final @NotNull @Nls String SHRUG = "🤷";
  private final @NotNull HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
  private Consumer<String> out;

  {
    try {
      out = ReplUtil.jlineDumbTerminalWriter();
    } catch (Exception _) {
      out = System.out::println;
    }
  }

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
      out.accept(SHRUG);
      return 1;
    }
    Doc markdown;
    LocalDateTime since;
    if (args.reportSince > 0) {
      since = LocalDate.now().minusDays(args.reportSince).atStartOfDay();
    } else if (args.reportSince < 0) {
      out.accept(SHRUG);
      return 1;
    } else since = sinceDate().atStartOfDay();
    if (args.repoName != null) {
      markdown = generate("Your Awesome Repository", args.repoName, since);
    } else {
      markdown = Doc.vcat(REPO
        .mapChecked(t -> generate(t.name, t.route, since))
        .view()
        .prepended(Doc.plain("## The Aya Theorem Prover")));
    }
    out.accept(markdown.debugRender());
    return 0;
  }

  public @NotNull Doc generate(@NotNull String name, @NotNull String repo, LocalDateTime since) throws IOException, InterruptedException {
    var req = buildRequest("https://api.github.com/repos/" + repo + "/pulls?state=closed&sort=updated&direction=desc&per_page=100");
    var seq = parse(client.send(req, HttpResponse.BodyHandlers.ofInputStream()).body())
      .view()
      .filter(i -> i.updatedAt.isAfter(since))
      .toSeq();
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

  public static HttpRequest buildRequest(String uri) {
    return HttpRequest.newBuilder().GET()
      .uri(URI.create(uri))
      .build();
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
      .registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) (json, _, _) ->
        LocalDateTime.parse(json.getAsJsonPrimitive().getAsString().replace("Z", "")))
      .create()
      .fromJson(new InputStreamReader(input), GsonClasses.PR[].class));
  }
}
