# Yet Another Bingo - Kits

为 Yet Another Bingo 提供「职业/Kit」系统的 Fabric 模组扩展。

## 功能简介

- 在 Bingo 对局开始前为玩家提供职业选择界面。
- 支持玩家通过命令提前预选职业。
- 对局开始时自动发放所选职业的装备。
- 未及时选择时会自动兜底分配职业，避免开局卡住。
- 支持通过 JSON 自定义职业内容（图标、描述、物品、附魔、组件等）。

## 依赖与版本

- Minecraft: 1.21.11
- Fabric Loader: 0.18.0+
- Fabric API
- Yet Another Bingo（依赖其 API）

版本号来自当前工程配置，详细请见 [gradle.properties](gradle.properties) 与 [build.gradle](build.gradle)。

## 使用方式

- 对局前根据提示打开职业选择菜单并确认。
- 命令：/yabkit choose
  - 用途：在开局前预选职业。
  - 说明：预选只会跳过下一局的职业弹窗，可重复执行修改。

## 配置文件

游戏启动后会在配置目录自动生成：

- config/yet_another_bingo_kits/mod_config.json
- config/yet_another_bingo_kits/kits.json

其中 kits.json 用于定义职业列表。默认模板来源于 [src/main/resources/yet_another_bingo_kits/default_kits.json](src/main/resources/yet_another_bingo_kits/default_kits.json)。

## 构建

```bash
./gradlew build
```

Windows 可使用：

```powershell
.\gradlew.bat build
```

构建产物位于：

- build/libs/

## License

GPL-3.0。详见 [LICENSE.txt](LICENSE.txt)。
