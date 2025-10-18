// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
import org.aya.compiler.morphism.ast.AstDecl;
import org.aya.compiler.serializers.ModuleSerializer;
import org.junit.jupiter.api.Test;

public class AstTest {
  @Test public void letElim() {
    var result = CompileTest.tyck("""
      open inductive Nat | zero | suc Nat
      def infix + (a b : Nat) : Nat
      | 0, b => b
      | suc a, b => suc (a + b)
      
      def test (n : Nat) : Nat =>
        let m := n + n
        in m + m
      """);
    var ser = new ModuleSerializer(result.info().shapeFactory());
    var anfBuilder = ser.serializeToANF(CompileTest.computeModuleResult(result));
    var anf = anfBuilder.build();
    var test = anf.members().filterIsInstance(AstDecl.Clazz.class)
      .find(cl -> cl.className().displayName().endsWith("test"))
      .get();
    System.out.println(test);
  }
}
