# 架构说明

## 模块划分

```
engine/   纯 Kotlin，无 Android 依赖，可单测
service/  无障碍服务，负责事件流与写回
data/     SharedPreferences 持久化（JSON 序列化自定义规则）
ui/       Jetpack Compose，Material 3
```

## 事件流

```
无障碍事件 (仅 com.tencent.mobileqq / mobileqqi)
 ├─ TYPE_WINDOW_STATE_CHANGED → 丢弃输入节点缓存，重新加载配置
 ├─ TYPE_VIEW_TEXT_CHANGED
 │    ├─ 实时模式            → process()
 │    └─ 标点模式            → 读输入框，句读结尾才 process()
 └─ TYPE_VIEW_CLICKED (send_btn) → process() 兜底

process():
 rootInActiveWindow → 按 QQ 包名和 viewId 找已聚焦的 input 框
 → recoverOriginal(raw) 还原用户原文
 → TransformEngine.transform(原文, 规则链, allowRandomTail)
 → 输入中使用非完成态规则；发送时使用完整规则
 → 与当前内容不同则 ACTION_SET_TEXT 写回 + 光标移到末尾
```

## 增量还原（recoverOriginal）

维护 `lastTarget`（上次写回的完整文本）与 `userOriginal`（累计的用户原文）：

- 若当前输入框内容以 `lastTarget` 为前缀 → 追加增量部分到 `userOriginal`
- 若当前文本等于 `lastTarget`，保留原文，不重复恢复。
- 用户修改了已经转换的内容时，保留其修改并暂停该消息的自动处理。
- 清空输入框或切换输入窗口后重置状态。顺序后缀按消息推进，不按写回次数推进。

### 为什么不逆向映射替换规则

替换和追加不是可逆操作。原文可能本来就包含「本喵」、后缀或颜文字。
1.7 移除了剥离器，不再通过删除这些字串来猜测原文。
手动修改后的暂停策略优先保护文本，恢复自动处理需要清空当前消息。

## 回显抑制

ACTION_SET_TEXT 写回后会再次触发 TYPE_VIEW_TEXT_CHANGED。当前内容与
`lastTarget` 相同且不是发送处理时直接忽略，避免重复变换。

## allowRandomTail

输入过程中只执行替换规则，并只为已经被句读符号结束的片段追加句尾后缀；
固定尾缀与随机颜文字都只在完整消息处理时追加。这样输入半句话不会提前
出现尾缀。发送点击事件可能在 QQ 已发出消息后到达，因此该事件只作为补充，
不能保证修改赶在发送之前完成。服务不会拦截、重发或撤回消息。

## Unicode 工具

`BidiTools` 独立于无障碍服务，负责昵称模板、控制字符清理、闭合序列和字符分析。
生成模板采用 RLI、LRO 和嵌套 RLI，尾部文字按码点逆序保存。
保护文本先闭合昵称中的未结束作用域，再按段落以 LRI/PDI 包裹正文。
加强模式额外追加三组 PDI/PDF。它是兼容性选项，不承诺对任意 QQ 排版都有效。
复制操作只在用户点击按钮时访问剪贴板，服务不再通过剪贴板回退写入。

预览由 Compose 的本机文本排版提供，不等同于 QQ 的真实提及组件。
QQ 的昵称长度限制没有硬编码。码点数和 UTF-16 长度只用于分析。
复合表情和组合字符作为尾部文字时需要另行核对，推荐使用普通文字。

## 扩展新目标应用

1. 在 `accessibility_service_config.xml` 的 `packageNames` 中追加目标包名
2. 在 Service 中为新包名定义 input / send_btn 的 viewId 与包名判断分支
3. 核对事件时序、焦点和写回行为，并补充该应用的测试。
