// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.module.DumbModuleLoader;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.util.TimeUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.UnaryOperator;

import static org.aya.compiler.serializers.NameSerializer.getClassName;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TailRecTest {
  private static JitCon O, S;
  private static JitFn const0, add, mult;
  private static DataCall NatCall;

  @BeforeAll public static void init() throws IOException {
    var stream = RedBlackTreeTest.class.getResourceAsStream("/TailRec.aya");
    assert stream != null;
    var code = new String(stream.readAllBytes());
    stream.close();
    var result = CompileTest.tyck(code);
    var baseDir = CompileTest.GEN_DIR.resolve("seqsum");
    var time = TimeUtil.profile(() -> CompileTest.serializeFrom(result, baseDir));
    System.out.println("Code generation time: " + TimeUtil.millisToString(time));
    var innerLoader = new URLClassLoader(new URL[]{baseDir.toUri().toURL()}, TailRecTest.class.getClassLoader());
    InstanceLoader tester = new InstanceLoader(innerLoader);

    var baka = DumbModuleLoader.DUMB_MODULE_NAME;

    O = tester.loadInstance(getClassName(baka.derive("Nat"), "O"));
    S = tester.loadInstance(getClassName(baka.derive("Nat"), "S"));

    JitData Nat = tester.loadInstance(getClassName(baka, "Nat"));
    NatCall = new DataCall(Nat, 0, ImmutableSeq.empty());

    const0 = tester.loadInstance(getClassName(baka, "const0"));
    add = tester.loadInstance(getClassName(baka, "add"));
    mult = tester.loadInstance(getClassName(baka, "mult"));
  }

  @Test public void basics() {
    Term term;

    var num = new IntegerTerm(696969696, O, S, NatCall);
    term = const0.invoke(UnaryOperator.identity(), ImmutableSeq.of(num));
    assertNotNull(term);
    System.out.println(term);

    var a = new IntegerTerm(12, O, S, NatCall);
    var b = new IntegerTerm(21, O, S, NatCall);
    term = add.invoke(UnaryOperator.identity(), ImmutableSeq.of(a, b));
    assertNotNull(term);
    System.out.println(term);

    var ma = new IntegerTerm(6969, O, S, NatCall);
    var mb = new IntegerTerm(4242, O, S, NatCall);
    var z = new IntegerTerm(0, O, S, NatCall);
    term = mult.invoke(UnaryOperator.identity(), ImmutableSeq.of(ma, mb, z));
    assertNotNull(term);
    System.out.println(term);
  }
}
