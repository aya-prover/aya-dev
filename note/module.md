# Aya 的 Module 设计

## Module 的名称

Module 的名称是一个满足 `weakId(::weakId)*` 的字符串，双冒号并不意味着嵌套关系。

## Module 的嵌套

Module 名称中的双冒号（在编译器内部）并不总是意味着嵌套关系。
实际上，如果我们声明了一个名字为 `A` 的 module，并且在 `A` 中声明了一个名字为 `B` 的 module。
当我们导入 `A` 时（实际上已经自动导入了），我们其实导入了：

* 名字为 `A` 的 module
* 名字为 `A::B` 的 module

理论上（至少在 module 的设计上），我们完全可以直接定义一个名字为 `A::B` 而不需要去在 `A` module 中定义 `B`
module（不过就无法在 `B` 中享受 `A` 的作用域了，但这并不是重点）

## Module 的导入

当我们导入一个 module 时，我们同时也会导入这个 module 所导出的所有 module。
正如上一节所示，我们导入 `A` module 时，同时也以名字 `A::B` 导入了它（自动）导出的 `B` module。
如果一个名字为 `A::B` 的 module 导出了名字为 `C::D` 的 module：当我们导入 `A::B` 时，同时也会以名字 `A::B::C::D`
导入 `C::D`；
而到底是 `A::B::C` 中的 `D` 模块还是 `A` 中的 `B::C::D` 模块或是其他的，我们完全不关心。

## Module 的歧义

当我们（直接或间接）导入的 module 所使用的名称与现有的 module 冲突，便发生了歧义。
如果这两个 Module 是同一个 Module，那么歧义便失去了它的意义，因此 Aya 不会为此困扰。
反之，Aya 会报告一个错误。
