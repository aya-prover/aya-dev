# Markdown 知乎发布预处理教程

[comment]: <> (@author ice1000)
[0]: https://zhuanlan.zhihu.com/p/69142198

知乎文章支持 markdown 导入，但是不支持 `$\LaTeX` 这种公式语法。
所以我写了个 gradle task 来做这件事，参照了[这篇教程][0]。
因为 gradle 的一些限制，导致暂时不能支持 markdown 中的公式多行，
但是可以渲染成多行的（比如用 `\\` 之类的）。

## 测试文本

怎么能允许 $\Gamma\vdash \mathcal U:\mathcal U$ 呢？
你们的 $\vdash_\ell$ 呢？
你们这些人的
$$ \frac{i<j}{\Gamma\vdash \mathcal U_i:\mathcal U_j} $$
哪去了呢？

## 构建命令

Windows 用户请使用 PowerShell，或者把那个 `./` 前缀去掉。

```shell
$ ./gradlew :docs:zhihu
```

或者

```shell
$ ./gradlew zhihu
```

然后构建后的 markdown 文件会出现在 `/docs/zhihu` 目录下，应该可以正确导入知乎。
打开[知乎 - 写文章](https://zhuanlan.zhihu.com/write)，工具栏右边有三个点，点一下可以导入。

相关参考资料：[GalAster/ZhihuLink](https://github.com/GalAster/ZhihuLink/issues/3)
