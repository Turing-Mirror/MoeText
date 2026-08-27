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
 rootInActiveWindow → 按 viewId 找 input 框，找不到则递归找 isEditable 节点
 → recoverOriginal(raw) 还原用户原文
 → TransformEngine.transform(原文, 规则链, allowRandomTail)
 → 输入中使用非完成态规则；发送时使用完整规则
 → 与当前内容不同则 ACTION_SET_TEXT 写回 + 光标移到末尾
```

## 增量还原（recoverOriginal）

维护 `lastTarget`（上次写回的完整文本）与 `userOriginal`（累计的用户原文）：

- 若当前输入框内容以 `lastTarget` 为前缀 → 追加增量部分到 `userOriginal`
- 否则（用户删改了中间、或清空重输）→ 用剥离器全量反推

剥离器（Stripper）按固定顺序反推装饰：

1. 移除固定尾缀与颜文字库中所有串（长串优先）
2. 保护替换规则的输出值（占位符替换，防止「本喵」里的喵被误删）
3. 移除句尾后缀字符
4. 折叠 3 个以上连续符号/表情串
5. 还原保护串

### 为什么不逆向映射替换规则

把「本喵」逆映射回「我」看似更彻底，但用户真实消息里可能本来就包含这些词，
会造成误伤。因此剥离器只移除「装饰」，保留用户自己打的字 —— 与原版行为一致。

## 回显抑制

ACTION_SET_TEXT 写回后会再次触发 TYPE_VIEW_TEXT_CHANGED。当前内容与
`lastTarget` 相同且不是发送处理时直接忽略，避免重复变换。

## allowRandomTail

输入过程中只执行替换规则，并只为已经被句读符号结束的片段追加句尾后缀；
固定尾缀与随机颜文字都只在完整消息处理时追加。这样输入半句话不会提前
出现尾缀，发送触发模式也可以等待发送按钮再处理整条消息。

## 扩展新目标应用

1. 在 `accessibility_service_config.xml` 的 `packageNames` 中追加目标包名
2. 在 Service 中为新包名定义 input / send_btn 的 viewId 与包名判断分支
3. 其余逻辑（引擎、剥离器、配置）无需改动
