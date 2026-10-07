# Yet Another Bingo - Kits

为 Yet Another Bingo 提供「职业/Kit」系统的 Fabric 模组扩展。

## 功能简介

- 在 Bingo 对局开始前为玩家提供职业选择界面。
- 点击职业即可确认选择并关闭界面。
- 支持玩家通过命令提前预选职业。
- 对局开始时自动发放所选职业的装备。
- 未及时选择时会自动兜底分配职业，避免开局卡住。
- 支持通过 JSON 自定义职业内容（图标、描述、物品、附魔、组件等）。

## 依赖与版本

- Minecraft Java Edition: 26.3
- Java: 25
- Fabric Loader: 0.19.5+
- Fabric API: 0.162.0+26.3 或更新的 26.3 版本
- Yet Another Bingo: 2.14.0+mc26.3
- Fabric Language Kotlin: 1.13.12+kotlin.2.4.0 或更新版本

版本号来自当前工程配置，详细请见 [gradle.properties](gradle.properties) 与 [build.gradle](build.gradle)。

## 使用方式

- 对局前打开职业选择菜单，点击一个职业即可选定。
- 命令：/kit
  - 用途：在开局前预选职业。
  - 说明：预选只会跳过下一局的职业弹窗，可重复执行修改。
  - 游戏进入开局或倒计时阶段后，命令不允许修改职业。

## 配置文件

游戏启动后会在配置目录自动生成：

- config/yet_another_bingo_kits/mod_config.json
- config/yet_another_bingo_kits/kits.json

其中 kits.json 用于定义职业列表。默认模板来源于 [src/main/resources/yet_another_bingo_kits/default_kits.json](src/main/resources/yet_another_bingo_kits/default_kits.json)。

## 构建

使用 JDK 25；Gradle Wrapper 会自动下载 Gradle 9.6.0。开发启动和游戏测试会自动下载对应的 Yet Another Bingo 模组。

```bash
./gradlew build
```

Windows 可使用：

```powershell
.\gradlew.bat build
```

构建产物位于：

- build/libs/

`build` 同时运行 26.3 服务端 GameTest，验证默认职业、附带的 29 个职业配置、物品组件与附魔、绑定装备限制、`/kit` 单击选择、开局发放和死亡重发。单独运行测试：

```bash
./gradlew runGameTest
```

升级时可保留已有的 `kits.json` 和 `mod_config.json`。

## License

GPL-3.0。详见 [LICENSE.txt](LICENSE.txt)。
