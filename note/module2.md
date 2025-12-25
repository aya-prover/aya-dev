鉴于目前 module 系统有点问题，重新梳理一遍 module 相关的代码。

## 语法层面

```aya
public open import foo::bar using (aaa as bbb)
```

会被解糖为

```aya
public import foo::bar
public open foo::bar using (aaa as bbb)
```

也就是说，被 import 进来的模块在被重新 re-export 的时候（注意这里 re-export 的是模块而不是它里面的东西），不会被修改。
因此我们可以用一个全局 id `QPath` 来引用每个模块。

同时，对于局部模块声明，它一定是 public 的，也就是说一定会被 export，这意味着我们有稳定的方式找到一个局部模块。
并且局部模块一定不会与文件级别的模块重名，比如 `foo/bar.aya` 和局部模块 `bar` in `foo.aya` 不会同时存在。
因此，我们可以用 `QPath` 来定位任意模块，并且通过 `load` 文件级别的模块，再获取局部模块来找到任意模块。

## `open`

在 `open` 一个模块时，会发生如下事情：

* 被 `open` 的模块的 `export` 内容被加载到当前模块的 `ModuleContext` 中，如果是 `public open`，那么同时还会被加载到当前模块的
  `export` 中。
* 如果该 `open` 包含重命名，并且该重命名的结果是一个运算符，那么会将该重命名存放在 `ResolveInfo#opRename` 中，并在之后的
  `StmtBinder` 中被应用到 `opSet` 上。
* 被 `open` 的模块 (ResolveInfo) 的 `shapeFactory`, `opSet` 和 `opRename` 会被导入。
  （注意 `opSet` 和 `opRename` 是高度绑定的，可以将 `opSet` 看作是对 `opRename` 求值后的结果，
  并且应该有 `opSet0 + opSet1 = eval(opRename0) + eval(opRename1) = eval(opRename0 + opRename1)`）
