// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Random;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.aya.compiler.serializers.NameSerializer.getClassName;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.normalize.Normalizer;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.syntax.literate.CodeOptions;
import org.junit.jupiter.api.Test;

public class RedBlackTreeTest {
  @Test public void test1() throws IOException {
    var stream = RedBlackTreeTest.class.getResourceAsStream("/TreeSort.aya");
    assert stream != null;
    var code = new String(stream.readAllBytes());
    stream.close();
    var result = CompileTest.tyck(code);

    var baseDir = CompileTest.GEN_DIR.resolve("redblack");
    Profiler.profileMany("Code Generation", 1, () -> CompileTest.serializeFrom(result, baseDir));
    var innerLoader = new URLClassLoader(new URL[]{baseDir.toUri().toURL()}, getClass().getClassLoader());
    var tester = new InstanceLoader(innerLoader);

    var baka = DumbModuleLoader.DUMB_MODULE_NAME;

    JitData List = tester.loadInstance(getClassName(baka, "List"));
    JitCon nil = tester.loadInstance(getClassName(baka.derive("List"), "[]"));
    JitCon cons = tester.loadInstance(getClassName(baka.derive("List"), ":>"));
    JitData Nat = tester.loadInstance(getClassName(baka, "Nat"));
    JitCon O = tester.loadInstance(getClassName(baka.derive("Nat"), "O"));
    JitCon S = tester.loadInstance(getClassName(baka.derive("Nat"), "S"));
    JitFn tree_sortNat = tester.loadInstance(getClassName(baka, "tree_sortNat"));

    var NatCall = new DataCall(Nat, 0, ImmutableSeq.empty());
    var ListNatCall = new DataCall(List, 0, ImmutableSeq.of(NatCall));

    IntFunction<Term> mkInt = i -> new IntegerTerm(i, O, S, NatCall);

    Function<ImmutableIntSeq, Term> mkList = xs -> new ListTerm(xs.mapToObj(mkInt), nil, cons, ListNatCall);

    var seed = 114514L;
    var random = new Random(seed);
    var largeList = mkList.apply(ImmutableIntSeq.fill(500, () -> random.nextInt(400)));
    var args = ImmutableSeq.of(largeList);

    var normalizer = new Normalizer(result.info().makeTyckState());
    var term = tree_sortNat.invoke(args);
    var sortResult = normalizer.normalize(term, CodeOptions.NormalizeMode.FULL);
    assertNotNull(sortResult);

    Profiler.profileMany("Code Execution", 5, () ->
      normalizer.normalize(tree_sortNat.invoke(args), CodeOptions.NormalizeMode.FULL));

    System.out.println(sortResult.easyToString());
  }
}
