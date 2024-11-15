# Aya 的 Module 设计

## ModulePath

ModulePath 是一个模块的绝对路径，它通常是以某个库为根到特定模块的物理路径。使用双冒号分隔每个路径的组成部分。

```aya
// 导入 arith/nat.aya 模块
import arith::nat
```

## ModuleName

尽管 ModuleName 和 ModulePath 有完全相同的表达式（用双冒号分隔），但 ModuleName 指的是在特定模块中，对某个模块的赋予的名字。
要注意的是，在这种情况下，双冒号不总是表达包含关系（尽管在正常的使用过程中，用户绝不可能构造出没有包含关系的 ModuleName）。

```aya
// 以默认名称 arith::nat 导入 arith/nat.aya 模块
import arith::nat
// 导入 arith::nat 模块中的所有符号和模块。
open arith::nat
```

## 上下文

由于 aya 的 tyck 具有顺序，因此我们不能在没有导入某个模块的情况下使用其内部的符号或模块。

## 歧义

在使用 `open` 时，通常会遇到歧义：`open` 的多个模块中有相同名字的符号，或者是当前模块中定义的符号与 `open` 导入的符号有相同的名字。
为了更简单地解决这个问题，我们要求模块不能导出歧义的符号（但只要不导出，模块中是可以有相同名字的符号的）。

```aya
// 假设 foo 和 bar 都定义了符号 baz
public open import arith::nat::foo
// public open import arith::nat::bar      // 报错：名字 baz 不能再被导出
open import arith::nat::bar                // 没有问题

// def = baz                               // 报错：我们不清楚要用哪个 baz
def = arith::nat::foo::baz                 // 没有问题
```

为了通过提供模块名来解决符号的歧义问题，我们需要模块名不能歧义
（也许我们可以通过其他方式来解决模块名的歧义，但那样得不偿失，我们通常不会有歧义的模块名，即使有，也可以通过重命名解决）

> 如果涉及歧义的所有符号名都指向同一个符号，就不会有报错

## 导入与开放

在导入/开放时，我们可以提供一些参数来影响它们的行为。

```aya
// 以名称 nat 导入 arith/nat.aya 模块 
import arith::nat as nat

// 将 nat 中除了 + 外的所有符号和模块导入当前模块
open nat hiding (+)

// 将 nat 中的 + 符号以名字 plus 导入当前模块
open nat using (+ as plus)
```

一般来说，用户是可以重复 `open` 同一个模块的。
