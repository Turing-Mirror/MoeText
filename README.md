# MoeText

基于 Android 无障碍服务的聊天文本自动美化工具：在你发出消息之前，按你定义的规则链自动改写 QQ 聊天输入框中的内容。

由 [图灵镜 Turing Mirror](https://github.com/Turing-Mirror) 开发维护

[![Licence](https://img.shields.io/badge/LICENSE-MIT-green.svg?style=flat-square)](./LICENSE)

**下载**　[GitHub Releases](https://github.com/Turing-Mirror/MoeText/releases)

**社媒**　[哔哩哔哩 @图灵镜](https://space.bilibili.com/3546871148579062)　·　[抖音 @图灵镜](https://v.douyin.com/6NxXcrKK9cc)（抖音号 `TuringMirror`）　·　[小红书 @图灵镜](https://www.xiaohongshu.com/user/profile/65f56bf1000000000b00e094)（小红书号 `TuringMirror`）　·　QQ 群 @图灵镜社区（群号 `1077458748`）

## 功能

- 规则链引擎，可自由组合：
  - 快捷替换（我 → 本喵 / 我们 → 本喵们 / 你 → 主人）
  - 自定义替换对（任意原文 → 替换词）
  - 句尾后缀库（多款，支持顺序轮换 / 随机抽取）
  - 固定尾缀库（多款，支持顺序轮换 / 随机抽取）
  - 随机颜文字库（内置 54 个，支持自定义）
- 两种触发模式：标点触发（推荐）/ 实时处理
- 发送按钮兜底处理、写入回显抑制、增量还原算法
- Material 3 界面，跟随系统深色模式，内置实时预览

## 安装

从 GitHub Releases 下载 APK 直接安装（需允许安装未知来源应用）。要求 Android 6.0（API 23）及以上。

## 使用步骤

1. 打开 MoeText
2. 在「状态」页点击「前往开启无障碍服务」，在系统设置中授权 MoeText
3. 在「规则」页配置想要的规则并保存
4. 回到 QQ 正常打字，消息会在发送前被自动改写

## 权限说明

- **无障碍服务**：监听 QQ 输入框的文本事件、读取输入框内容、执行写回
- **网络权限**：仅用于启动时/手动检查更新（读取 Releases 仓库的版本信息文件）。应用不收集、不上传任何数据

## 风险与免责声明

- 本工具会自动化改写您发出的消息，可能违反腾讯 QQ 用户协议，存在账号被限制功能或封禁的风险。
- 本项目仅供学习与研究，请自行评估并承担使用风险。
- MoeText 与腾讯官方没有任何关联；项目名称与代码中不使用任何官方商标。

## 构建

- 推荐：Android Studio 打开项目直接构建（自带 JDK 17）
- 命令行：`./gradlew assembleDebug`，产物位于 `app/build/outputs/apk/`

## 项目结构

```
app/src/main/java/com/turingmirror/moetext/
├── engine/      规则链引擎、剥离器（详见 docs/ARCHITECTURE.md）
├── service/     无障碍服务（事件流、增量还原、写回）
├── data/        配置持久化
└── ui/          Compose 界面与主题
```

## License

[MIT](LICENSE) © 2026 Turing Mirror

## 致谢

灵感来源于社区匿名作品「QQ喵喵助手」。本仓库为独立的全新实现（clean-room），未复用其任何代码或资源。
