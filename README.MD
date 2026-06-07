# MCToDC 

[English](#english) | [繁體中文](#繁體中文)

---

## English

MCToDC is a lightweight Minecraft 1.7.10 Forge modification built on the RetroFuturaGradle development framework. It provides a highly integrated, bidirectional bridge between a Minecraft server and Discord, featuring a built-in player identity verification and whitelisting mechanism.

### Key Features
* **Native REST API Integration:** Communicates directly with the Discord API v9 via Java's native standard HTTP library, requiring no massive third-party library dependencies.
* **Asynchronous Webhook & Communication:** Handles chat routing, console logs, commands, deaths, and achievements using a dedicated background thread pool (`MCToDC-Async-Executor`) to maintain optimal server tick rates.
* **Player Isolation & Verification Mechanism:** Unverified players are automatically isolated upon connecting, prompted with a clear UI showing a unique 4-digit verification token, and required to execute `!verify <code>` in the designated Discord channel to gain server access.
* **Dual Database Backend Support:** Supports local JSON text file binding storage (`mctodc-bnd.json`) alongside scalable relational MySQL database integration for seamless cross-server operations.
* **Hardware-Bound Token Encryption:** Uses a unique hardware signature (derived from system properties and CPU core details) via AES encryption to store Discord tokens and database passwords securely at rest.

### Workspace Setup & Compilation
Ensure you use the provided Gradle wrapper and configured Java 8 toolchain (Azul Zulu JDK).

* **Setup Development Environment (Optional):**
  * **Linux/macOS:** `./gradlew setupDecompWorkspace`
  * **Windows:** `gradlew.bat setupDecompWorkspace`
  * *Note: Remember to refresh or re-import the Gradle project in your IDE (IntelliJ IDEA / Eclipse) after running this command.*

* **Compile and Build:**
  * **Linux/macOS:** `./gradlew build`
  * **Windows:** `gradlew.bat build`
  * The compiled binary can be found under `build/libs/`.

* **Running the Test Environment:**
  * **Client:** `./gradlew runClient`
  * **Server:** `./gradlew runServer`

### Configuration Files
Upon the first boot, two configuration files are automatically generated under the server's `config/` directory:
1. `rfg-discord-bridge.toml`: Handles infrastructural parameters, including API tokens, channel IDs, Discord webhooks, SQL database credentials, and background status update intervals.
2. `rfg-discord-messages.toml`: Contains all localized and customizable string templates for chat formatting, announcements, death text extensions, achievement styles, and in-game kick screen panels.

### License
* **Custom Code:** Licensed under the Custom Code License. You are free to modify, compile, and use the source code for personal or server operations. Direct distribution or re-uploading of the compiled binary `.jar` files to third-party platforms is strictly prohibited.
* **Build Template:** The Gradle build layout and workspace scripts are dedicated to the public domain under the Creative Commons CC0 1.0 Universal License.

---

## 繁體中文

MCToDC 是一款基於 RetroFuturaGradle 開發框架構建的輕量化 Minecraft 1.7.10 Forge 伺服器模組。本模組旨在建立 Minecraft 遊戲內與 Discord 社群之間的雙向溝通橋樑，並內建嚴謹的玩家身份雙重驗證與白名單綁定機制。

### 核心特性
* **原生 REST API 整合：** 全程透過 Java 原生標準 HTTP 網路庫與 Discord API v9 進行直接通訊，完全不依賴外部大型第三方框架，維持模組整體的純淨度。
* **非同步 Webhook 與通訊處理：** 遊戲聊天轉發、控制台日誌攔截、指令監聽、玩家死亡及成就觸發，皆透過獨立的背景執行緒池（`MCToDC-Async-Executor`）進行非同步排程，確保不佔用主執行緒或干擾伺服器 TPS。
* **進服隔離與帳號驗證機制：** 未驗證玩家登入時會被自動拒絕並顯示內建引導面板，玩家可在畫面上取得一組隨機的四位數驗證碼，並至指定 Discord 頻道輸入 `!verify <驗證碼>` 完成綁定。
* **雙軌制資料庫後端：** 支援本地 JSON 文字檔案（`mctodc-bnd.json`）的快速資料儲存，同時支援關聯式 MySQL 資料庫整合，便於多伺服器或跨服白名單連動架構。
* **硬體特徵綁定加密：** 安全性高。Bot Token 與資料庫密碼在首次輸入後，系統會結合伺服器作業系統特徵與 CPU 核心數運算出專屬密鑰，並透過 AES 加密演算法將敏感欄位動態模糊化。

### 開發環境設置與編譯
請確保使用項目內附帶的 Gradle Wrapper，並確認系統已就緒 Java 8（推薦使用隨附配置之 Azul Zulu JDK）之工具鏈鏈接。

* **初始化開發工作區（選填）：**
  * **Linux/macOS：** `./gradlew setupDecompWorkspace`
  * **Windows：** `gradlew.bat setupDecompWorkspace`
  * *提示：執行完畢後，請務必重新整理或匯入 IDE (IntelliJ IDEA / Eclipse) 的 Gradle 模型以正確載入 Minecraft 與 Forge 核心類別。*

* **專案編譯與打包：**
  * **Linux/macOS：** `./gradlew build`
  * **Windows：** `gradlew.bat build`
  * 編譯完成的成品將輸出於 `build/libs/` 目錄下。

* **啟動測試環境：**
  * **客戶端：** `./gradlew runClient`
  * **伺服器端：** `./gradlew runServer`

### 設定檔說明
當模組首次成功加載時，將於伺服器 `config/` 目錄下自動產生兩個主要設定檔：
1. `rfg-discord-bridge.toml`：用於配置核心基礎設施，包含 Bot 金鑰、頻道 ID、Webhook 連結、SQL 連線憑證及背景狀態更新頻率等。
2. `rfg-discord-messages.toml`：容納所有本地化與客製化的訊息文本，包含聊天格式化格式、進出服公告、成就解鎖樣式及遊戲內隔離踢出畫面的字樣。

### 授權條款
* **模組客製化原始碼：** 採用專屬自訂原始碼授權。允許任何人出於個人或伺服器營運目的自由複製、修改與自行編譯原始碼。然而，嚴禁將編譯後的成品（如 `.jar` 檔案）直接轉載、分享或上傳至任何第三方平台。
* **構建腳本範本：** 本專案之配置架構與構建腳本範本皆放棄版權，全面釋出至 公共領域（Creative Commons CC0 1.0 Universal）。