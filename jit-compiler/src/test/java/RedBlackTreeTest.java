// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Random;

import static org.aya.compiler.serializers.NameSerializer.getClassName;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.repr.ListTerm;
import org.aya.util.TimeUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RedBlackTreeTest {
  private static JitCon nil, cons;
  private static JitCon O, S;
  private static JitFn tree_sortNat;
  private static DataCall NatCall, ListNatCall;

  @BeforeAll public static void init() throws IOException {
    var stream = RedBlackTreeTest.class.getResourceAsStream("/TreeSort.aya");
    assert stream != null;
    var code = new String(stream.readAllBytes());
    stream.close();
    var result = CompileTest.tyck(code);
    var baseDir = CompileTest.GEN_DIR.resolve("redblack");
    TimeUtil.profileMany("Code generation", 1, () -> CompileTest.serializeFrom(result, baseDir));
    var innerLoader = new URLClassLoader(new URL[]{baseDir.toUri().toURL()}, RedBlackTreeTest.class.getClassLoader());
    InstanceLoader tester = new InstanceLoader(innerLoader);

    var baka = DumbModuleLoader.DUMB_MODULE_NAME;

    JitData List = tester.loadInstance(getClassName(baka, "List"));
    nil = tester.loadInstance(getClassName(baka.derive("List"), "[]"));
    cons = tester.loadInstance(getClassName(baka.derive("List"), ":>"));
    JitData Nat = tester.loadInstance(getClassName(baka, "Nat"));
    O = tester.loadInstance(getClassName(baka.derive("Nat"), "O"));
    S = tester.loadInstance(getClassName(baka.derive("Nat"), "S"));
    tree_sortNat = tester.loadInstance(getClassName(baka, "tree_sortNat"));

    NatCall = new DataCall(Nat, 0, ImmutableSeq.empty());
    ListNatCall = new DataCall(List, 0, ImmutableSeq.of(NatCall));
  }
  private static Term mkInt(int i) { return new IntegerTerm(i, O, S, NatCall); }
  private static Term mkList(ImmutableIntSeq xs) {
    return new ListTerm(xs.mapToObj(RedBlackTreeTest::mkInt), nil, cons, ListNatCall);
  }

  @Test public void sameInput() {
    var random = new Random(114514L);
    var largeList = ImmutableIntSeq.fill(500, () -> random.nextInt(400));
    System.out.println("Input: " + largeList);
    var args = ImmutableSeq.of(mkList(largeList));

    var term = tree_sortNat.invoke(args);
    assertNotNull(term);
    System.out.println(term.easyToString());

    TimeUtil.profileMany("Running many times on the same input...", 5, () ->
      tree_sortNat.invoke(args));
  }

  @Test public void varyingInput() {
    var random = new Random(1919810L);
    TimeUtil.profileMany("Running many times on new inputs....", 5, () -> {
      var newList = mkList(ImmutableIntSeq.fill(500, () -> random.nextInt(400)));
      tree_sortNat.invoke(ImmutableSeq.of(newList));
    });
  }
}
