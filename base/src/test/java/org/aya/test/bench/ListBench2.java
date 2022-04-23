/*
// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.bench;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.repl.ReplCompiler;
import org.aya.cli.single.CliReporter;
import org.aya.generic.util.NormalizeMode;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.SingleShotTime)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Threads(8)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class ListBench2 {

  final @NotNull ReplCompiler replCompiler;
  final @NotNull Path benchFile;
  @Param({"ten", "hundred", "two-k", "four-k", "eight-k"})
  private String param;

  public ListBench2() {
    benchFile = Path.of(ListBench2.class.getTypeName() + ".aya");

    var reporter = CliReporter.stdio(true, DistillerOptions.debug(), Problem.Severity.WARN);
    replCompiler = new ReplCompiler(ImmutableSeq.empty(), reporter, null);
    try {
      replCompiler.loadToContext(benchFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) throws RunnerException {
    var argSeq = ImmutableSeq.from(args);
    var outputPath = argSeq.first();
    new ListBench();

    var opt = new OptionsBuilder()
      .include(ListBench.class.getSimpleName())
      .resultFormat(ResultFormatType.TEXT)
      .result(outputPath)
      .build();

    new Runner(opt).run();
  }

  @Benchmark
  public void normBench(@NotNull Blackhole bh) {
    bh.consume(replCompiler.compileExpr("test " + param, NormalizeMode.NF));
  }

  */
/*@Benchmark
  public Term normBase() {
    return replCompiler.compileExpr("(suc (suc zero))", NormalizeMode.NF);
  }*//*

}
*/
