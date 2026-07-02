# PSocks

PSocks 是一个 Android 端的 SOCKS5 代理客户端。它借助系统的 VPN 接口把设备流量整体接管下来，再转发到你指定的 SOCKS5 代理服务器，从而实现全局或分应用的代理转发。

## 功能特性

- 分应用路由（三种模式）：仅所选应用走 VPN、所有应用走 VPN、所选应用绕过 VPN。
- IPv4 / IPv6 双栈开关，可分别启用。
- 远程 DNS 映射，或指定本地 DNS 服务器（IPv4/IPv6）。
- SOCKS5 用户名 / 密码认证。
- 可选的独立 UDP relay 主机，以及 UDP over TCP。
- 前台服务常驻通知，开机自启动（BootReceiver）。

## 技术实现

### 整体架构

PSocks 由两部分组成：上层的 Java/Android 应用负责界面、配置与系统 VPN 的建立；底层的原生引擎负责真正的流量转换（tun2socks）。两者通过 JNI 交互。

```
应用流量
   │
   ▼
Android VpnService ── 建立 TUN 虚拟网卡 ──► tun 文件描述符 (fd)
   │                                              │
   │  (fd + YAML 配置路径)                         │
   ▼                                              ▼
ProxyVpnService  ── JNI: TProxyStartService ─► 原生引擎 (libpsocks-engine.so)
                                                  │  在用户态解析 IP 包，
                                                  │  用 lwIP 还原 TCP/UDP，
                                                  ▼  再按 SOCKS5 协议转发
                                            SOCKS5 代理服务器
```

### 核心：原生 tun2socks 引擎

原生部分位于 `app/src/main/jni/psocks-engine`，基于开源项目 **HevSocks5Tunnel**（`heiher/hev-socks5-tunnel`），是一个轻量的 tun2socks 实现。它做的事情是：从 TUN fd 读取原始 IP 包，在用户态协议栈里还原成 TCP 连接和 UDP 会话，再按 SOCKS5 协议与代理服务器建连、转发数据，最后把返回的数据重新封成 IP 包写回 fd。

关键组成：

- **lwIP**（`third-part/lwip`）：一个轻量级用户态 TCP/IP 协议栈，负责在不经过内核的情况下解析/组装 IP、TCP、UDP、ICMP 等。
- **hev-task-system**（`third-part/hev-task-system`）：协程/任务调度框架，用少量线程承载大量并发连接，兼顾性能与内存占用。
- **libyaml**（`third-part/yaml`）：解析上面生成的 YAML 配置。
- 引擎源码（`src/`）实现了 SOCKS5 会话（TCP/UDP）、UDP-in-UDP、UDP-in-TCP、全锥形 NAT，以及远程 DNS 映射（`hev-mapped-dns.c`，把内网段 `240.0.0.0/4` 的假地址映射为域名，通过代理做远程解析，避免 DNS 泄漏）。

## 构建

用 Android Studio 打开本目录，或命令行执行：

```bash
./gradlew assembleDebug
```

构建依赖：Android SDK（compileSdk 34）、NDK（`ndkVersion 29.0.14033849`）。原生代码通过 `ndkBuild`（`src/main/jni/Android.mk`）编译，产物为各 ABI 下的 `libpsocks-engine.so`，目标 ABI 为 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`。`minSdk 24`，`targetSdk 34`。

## 使用说明

在主界面填写 SOCKS5 服务器地址与端口（需要认证再填用户名/密码），按需设置 DNS 与 IPv4/IPv6，选择分应用路由模式（如需指定应用，点击 Choose apps），然后打开顶部开关；首次会弹出系统 VPN 授权。连接成功后状态显示 Connected，并在通知栏常驻。

## 致谢

原生转发引擎基于 [HevSocks5Tunnel](https://github.com/heiher/hev-socks5-tunnel)（作者 heiher），遵循其开源许可。
