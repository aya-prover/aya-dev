// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.normalize.Normalizer;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.literate.CodeOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.aya.compiler.AbstractSerializer.javify;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RedBlackTreeTest {
  @Test public void test1() throws IOException {
    var stream = RedBlackTreeTest.class.getResourceAsStream("/TreeSort.aya");
    assert stream != null;
    var code = new String(stream.readAllBytes());
    stream.close();
    var result = CompileTest.tyck(code);

    var tester = new CompileTester(CompileTest.serializeFrom(result));
    var genDir = Paths.get("src/test/gen");
    Files.createDirectories(genDir);
    Files.writeString(genDir.resolve("baka.java"), tester.code);
    tester.compile();

    JitData List = tester.loadInstance("baka", "List");
    JitCon nil = tester.loadInstance("baka", "List", javify("[]"));
    JitCon cons = tester.loadInstance("baka", "List", javify(":>"));
    JitData Nat = tester.loadInstance("baka", "Nat");
    JitCon O = tester.loadInstance("baka", "Nat", "O");
    JitCon S = tester.loadInstance("baka", "Nat", "S");
    JitFn tree_sortNat = tester.loadInstance("baka", "tree_sortNat");

    var NatCall = new DataCall(Nat, 0, ImmutableSeq.empty());
    var ListNatCall = new DataCall(List, 0, ImmutableSeq.of(NatCall));

    IntFunction<Term> mkInt = i -> new IntegerTerm(i, O, S, NatCall);

    Function<ImmutableIntSeq, Term> mkList = xs -> new ListTerm(xs.mapToObj(mkInt), nil, cons, ListNatCall);

    var seed = 114514L;
    var random = new Random(seed);
    var largeList = mkList.apply(ImmutableIntSeq.fill(250, () -> random.nextInt(200)));
    var args = ImmutableSeq.of(largeList);

    var normalizer = new Normalizer(result.info().makeTyckState());
    var sortResult = normalizer.normalize(tree_sortNat.invoke(null, args), CodeOptions.NormalizeMode.FULL);
    assertNotNull(sortResult);

    Profiler.profileMany(5, () ->
      normalizer.normalize(tree_sortNat.invoke(null, args), CodeOptions.NormalizeMode.FULL));

    System.out.println(sortResult.debuggerOnlyToString());
  }
}
