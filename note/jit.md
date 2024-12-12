# Jit Specification

## Jit Unit

Jit Unit 是一个需要被编译的单位, 它包括:

* 任何 Def
* 顶级模块/子模块

## Class Structure

所有 Jit Unit 都会被编译成一个 class.

示例: `class-structure.aya`

```aya
inductive Nat
| O
| S Nat

def inc (n : Nat) : Nat => S n 
```

编译输出: `class$45structure.java`

```java
public final class class$45structure {
  public static final class class$45structure$Nat extends JitData {
    // ...
  }

  public static final class class$45structure$Nat$O extends JitCon { /* ... */
  }

  public static final class class$45structure$Nat$S extends JitCon { /* ... */
  }

  public static final class class$45structure$inc extends JitFn { /* ... */
  }
}
```

注意到 `Nat` 的构造子在序列化在外层, 并且所有 Jit Unit 的类名都有一个所在模块的前缀.
这是由于某些技术问题, 所有子模块的内容都会序列化到文件模块内.

> 关于编译结果中的类名部分，参见 [Name Mapping](#name-mapping) 章节

任何 Def 对应的类都需要继承对应的基类, 如 `JitData` 对应归纳数据类型, `JitCon` 对应构造子.

## Metadata

除了基类要求实现的各种方法, 这些类还需要带有 `@CompiledAya` 注解, 以提供其他信息, 如:

* 在 aya 中的符号名
* shape 信息
* 结合性信息
* 等等...

## Name Mapping

> ![WARNING]
> 这个章节仅适用于编译到 Java 源代码的编译器实现.

由于需要在 Java 编译器的限制, 并不是所有合法的 aya 符号名在 Java 中都合法, 因此需要对符号名进行转义.

* 对于是关键字的符号名, 将它转义成 `_$<keyword>`. 如 `new` 转义成 `_$new`
* 对于符号名中的每个 `$`, 将它转义成 `$$`.
* 对于符号名中的每个不合法的 Java 符号名 code point, 将它转义成 `$<code point>`. 如 `+` 转义成 `$43`.

## 模式匹配

变量:

* `Term[] result` 保存模式匹配中的 binding 所匹配的 term.
* `int matchState` 保存模式匹配的状态, 如果这个状态大于 0, 则表示已经匹配到对应的 clause.
* `boolean subMatchState` 保存局部模式匹配的状态, 有些情况下需要尝试 fastpath, 如 `Pat.ShapedInt` 会先尝试将 term 转换为
  `IntegerTerm`,
  如果失败再进行朴素匹配. 此时, 如果 fastpath 匹配成功, 则 `subMathState` 会被设置为 `true`, 从而跳过朴素匹配.

示例: `nat.aya`

```aya
def plus Nat Nat : Nat
| 0, b => b
| S a, b => S (plus a b)
```

编译输出: `nat.java`

```java
import java.util.function.Supplier;

// 忽略不重要的内容

public Term invoke(Supplier<Term> onStuck, Term arg0, Term arg1) {
  Term[] result = new Term[2];    // 所有 clause 中, 最大的 binding 数量是 2
  int matchState = 0;
  boolean subMatchState = false;

  // 用于从代码块中跳出
  do {
    // 第一个 clause
    // 匹配: 0
    subMatchState = false;
    if (arg0 instanceof IntegerTerm var0) {
      if (0 = arg0.repr()) {
        subMatchState = true;
      }
    }

    if (!subMatchState) {
      if (arg0 instanceof ConCallLike var0) {
        if (Nat$O.INSTANCE.ref() == var0.ref()) {
          // 匹配: b
          // b 是一个 Pat.Bind, 匹配总是成功
          result[0] = var1;
          matchState = 1;
        } else {
          matchState = 0;   // mismatch
        }
      } else {
        matchState = -1;    // stuck
      }
    }

    // 第一个 clause 结束, 检查是否匹配成功
    if (matchState > 0) break;

    // 省略第二个 clause 的匹配
    // ...
  } while (false);

  // 根据 matchState 决定行为
  switch (matchState) {
    case -1 -> {
      return onStuck.get();
    }
    case 0 -> {
      // 由于有 pattern classifier, 这个 case 是不可达的.
      return Panic.unreachable();
    }
    case 1 -> {
      // | 0, b => b
      return result[0];
    }
    case 2 -> {
      // | S a, b => S (plus a b)
      return new ConCall(Nat$S.INSTANCE, 0, ImmutableSeq.of(plus.invoke(
        () -> new FnCall(/* ... */),
        result[0], result[1]
      )));
    }
    default -> {
      return Panic.unreachable();
    }
  }
}
```
