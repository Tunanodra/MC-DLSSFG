# MC-DLSSFG (MCDLSSG)

**Tunanodra** 的个人作品。

在 Minecraft 里接上现代超分和帧生成：DLSS、XeSS、FSR2，以及完整的 **DLSS FG（Frame Generation）** 支持。

> 灵感来自 [IReallyWantToSleep/superresolution](https://github.com/IReallyWantToSleep/superresolution)


---

## 现在能做什么

| 能力 | 状态 |
|------|------|
| DLSS 超分 | 有 |
| XeSS 超分 | 有 |
| FSR2 超分 | 有 |
| **DLSS FG 帧生成** | 有（完整链路） |
| 加载器 | Forge **1.20.1**（当前主目标） |

### DLSS FG 实测范围

帧生成目前**只在 Windows + NVIDIA GPU** 上测通过。

- 系统：Windows 10 / 11 x64  
- 显卡：支持 DLSS FG 的 NVIDIA 卡（通常是 40 系及更新，以驱动与 NGX 实际能力为准）  
- 其它平台 / 厂商：超分部分另说；**FG 请先当 Windows + NVIDIA only**

驱动、游戏内设置、光影兼容都会影响 FG。出问题先更新 Game Ready / Studio 驱动再试。

---

## 要求

- Minecraft **1.20.1** + Forge  
- 建议 OpenGL 4.3+；走 NGX / Streamline / 互操作路径时还需要能用的 Vulkan 环境
- 编 native：Windows 上要 CMake + 合适的 C++ 工具链（见 `native/` 与 `native/cpp/docs/build.md`）

第三方 SDK（DLSS / XeSS / FSR 等）走 **git submodule**

```bash
git clone --recurse-submodules https://github.com/Tunanodra/MC-DLSSFG.git
# 已 clone 的话：
git submodule update --init --recursive
```

---

## 构建

```bash
# 1) 拉 submodule
git submodule update --init --recursive

# 2) 编 C++ 本机库（产出进 common 资源 lib 目录，已被 gitignore）
./gradlew :native:buildNative

# 3) 编模组
./gradlew build
```

具体开关看根目录 `gradle.properties`（版本、是否 dev、是否自动下库等）  
CI 工作流在 `.github/workflows/`。

---

## 项目结构

```
common/     共享 Java：超分、FG、GUI、兼容层
forge/      Forge 1.20.1 入口与 mixin
native/     C++ / CMake：NGX、Streamline、XeSS、主库
configs/    多版本构建配置
libs/       少量本地依赖 jar
```

---

## 声明

1. **作者**：Tunanodra（个人项目，事实上是为了我打算开的服务器做的mod）
2. **启发**：[superresolution](https://github.com/IReallyWantToSleep/superresolution) —— 感谢原作者把「MC 里接超分」这条路走通。  
3. **商标**：DLSS、XeSS、FSR、Minecraft、Forge 等名称归各自权利人；本项目与 NVIDIA / Intel / AMD / Mojang / Microsoft 无官方关联。  
4. **风险**：改渲染管线的模组，和光影、性能模组叠一起可能炸。出问题先关 FG / 换算法再排查。
5. **光影**: 目前已经测试的可以工作的光影是: Photonv1.2a , ComplementaryUnbound_r5.6.1

---

## 许可证

本仓库**自有代码**使用 **[MIT License](./LICENSE)**

捆绑或链接的第三方组件（尤其是 NVIDIA DLSS / Streamline、Intel XeSS、AMD FidelityFX 等）**仍受原厂许可约束**，MIT 盖不到它们。许可证摘录见：

- `common/src/main/resources/licenses/`
- 各 submodule 上游仓库

分发二进制模组时，请自行确认你对 NGX / Streamline / XeSS 等运行时库的再分发权利。

