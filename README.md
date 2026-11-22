# Auto Name Tag

**No name tags needed. Every mob in your world, named the way you want.**

Auto Name Tag gives a name to mobs the moment they spawn or are bred, picked at random from **a name list you write for each kind of mob**. No more hunting for name tags and renaming animals one by one at an anvil.

[![Download on CurseForge](https://img.shields.io/badge/CurseForge-Download-orange)](https://www.curseforge.com/minecraft/mc-mods/auto-name-tag)
![Minecraft 1.21 / 1.21.1](https://img.shields.io/badge/Minecraft-1.21%20%7C%201.21.1-brightgreen)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue)
![License MIT](https://img.shields.io/badge/License-MIT-lightgrey)

---

## What makes it different

### Your own name list for every mob type

This mod doesn't hand out names from a fixed built-in list. **You decide which mobs get names and what those names are.** Add as many names as you like for sheep, cows, creepers and so on. Any language works. Mobs without a list stay unnamed, so you only name what you want.

### The Animal Guide: edit names while looking at the mob

Open the Animal Guide book and each spread shows the mob on the left and its names on the right. **Add and remove names, and add or delete whole pages, right inside the book.** You never have to open a config file.

- You receive one copy the first time you join a world (or craft it from a book and wheat)
- Jump to any mob from the contents page, or search by mob name, ID or registered name
- Add a page with one click from the list of mobs that have no names yet
- Change settings on the last page

### Babies get names too

Besides natural spawns, world generation, village animals, wandering traders, patrols, raids and spawn eggs, **babies born from breeding are named as well**.

### Easter-egg names, on your terms

Optionally give vanilla's special names such as `jeb_` (rainbow sheep), `Dinnerbone` (upside down) and `Toast` to **any mob, at a chance you choose**. The toggle and the chance (0–100%) can both be changed in game.

### Works with mobs from other mods

Mobs added by other mods are detected automatically and get their own entry in the name list, so you can name them from the guide without looking up IDs.

### No restart required

Name edits and setting changes apply **immediately while the game is running**.

---

## How to use

1. Install the mod and join a world. An Animal Guide appears in your inventory.
2. Right-click the guide, open the page of a mob, and add names.
3. Mobs that spawn or are bred from then on receive a name.

To name mobs that already exist, run `/animalnamer nameall`.

## Config files

You can also edit the text files directly.

- `mods/names.txt` — the name lists
- `config/animalnamer.properties` — settings

```
[sheep]
Fluffy
Woolly

[cow]
Bessie
```

## Commands

All commands require operator permission.

| Command | Description |
|---|---|
| `/animalnamer add <mob> <name>` | Add a name |
| `/animalnamer remove <mob> <name>` | Remove a name |
| `/animalnamer list [mob]` | Show registered names |
| `/animalnamer nameall` | Name every unnamed mob in loaded chunks |
| `/animalnamer showname on｜off` | Always show names above mobs |
| `/animalnamer special on｜off` | Give special names to all mobs |
| `/animalnamer special chance <0-100>` | Chance of a special name |
| `/animalnamer reload` | Reload the config files |

## Requirements

- Minecraft 1.21 / 1.21.1
- Fabric Loader 0.15.0 or newer
- Fabric API (required)
- In multiplayer, the mod is required on **both the server and every client**

## Notes

- Available in **English and Japanese**. The guide and chat messages follow your game language.
- Editing names in the guide and changing settings is limited to operators and the world owner.
- Names given by this mod do not stop mobs from despawning (unlike a name tag).
- A vindicator named `Johnny` attacks other mobs.

---

## Building from source

```bash
./gradlew build
```

The built jar is placed in `build/libs/`.

## License

MIT — see [LICENSE](LICENSE).

---

# 日本語

**名札いらず。あなたの世界のMOBみんなに、あなたが決めた名前を。**

ヒツジには「もふもふ」、ウシには「もーちゃん」。Auto Name Tag は、MOBがスポーンしたり繁殖で生まれたりした瞬間に、**MOBの種類ごとに用意した名前リスト**からランダムで名前を付けるMODです。名札を集めて金床で1匹ずつ名付ける必要はもうありません。

## 特徴

- **MOBの種類ごとに、自分だけの名前リスト** — どのMOBにどんな名前を付けるかは、すべてあなたが決めます。日本語の名前もそのまま使えます
- **「どうぶつ名鑑」で編集** — 見開きの左にMOBの姿、右に名前の一覧。設定ファイルを開かずに、本の中で追加・削除ができます
- **繁殖で生まれた子どもにも** 名前が付きます
- **特別な名前** — `jeb_`（虹色のヒツジ）、`Dinnerbone`（逆さま）などを、確率を決めて全MOBに付けられます
- **他のMODのMOBにも対応** — 自動で見つけて名前リストに項目を用意します
- **再起動なしで反映**

## 使い方

1. MODを入れてワールドに入ると「どうぶつ名鑑」が1冊届きます
2. 名鑑を右クリックで開き、名前を付けたいMOBのページで名前を追加します
3. それ以降にスポーン・繁殖したMOBに名前が付きます

すでにいるMOBにまとめて名前を付けたいときは `/animalnamer nameall` を使います。

## 動作環境

Minecraft 1.21 / 1.21.1、Fabric Loader 0.15.0以上、Fabric API（必須）。
マルチプレイでは、サーバーと参加者全員に導入が必要です。

## 注意

- 名鑑での編集と設定変更ができるのは、OP権限のある人とワールドの持ち主です
- 名前が付いても、MOBのデスポーンの仕組みは変わりません（名札と違い、デスポーンを防ぐ効果はありません）
- 特別な名前 `Johnny` が付いたヴィンディケーターは、他のMOBを襲うようになります

## ライセンス

MIT
