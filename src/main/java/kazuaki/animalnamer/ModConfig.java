package kazuaki.animalnamer;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * config/animalnamer.properties を読み込んでMODの動作設定を管理するクラス。
 */
public final class ModConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnimalNamerMod.MOD_ID);
    private static final String KEY_SHOW_NAME_ALWAYS = "show_name_always";
    private static final String KEY_SPECIAL_NAMES_ENABLED = "special_names_enabled";
    private static final String KEY_SPECIAL_NAMES_CHANCE = "special_names_chance";

    private static volatile boolean showNameAlways = false;
    private static volatile boolean specialNamesEnabled = false;
    private static volatile int specialNamesChance = 100;
    private static Path configPath;

    private ModConfig() {
    }

    public static void load() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("animalnamer.properties");

        // 既存のファイルがあれば読み込み、新しい設定項目と説明を補って書き直す
        if (Files.exists(configPath) && !reload()) {
            return;
        }
        try {
            save();
        } catch (IOException e) {
            LOGGER.error("[Auto Name Tag] Failed to create animalnamer.properties: {}", e.getMessage());
        }
    }

    /**
     * 設定を読み直す。失敗した場合は読み込み済みの内容を維持する。
     *
     * @return 読み込みに成功したら true
     */
    public static boolean reload() {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            LOGGER.error("[Auto Name Tag] Failed to read animalnamer.properties: {}", e.getMessage());
            return false;
        }

        showNameAlways = Boolean.parseBoolean(properties.getProperty(KEY_SHOW_NAME_ALWAYS, "false").trim());
        specialNamesEnabled = Boolean.parseBoolean(properties.getProperty(KEY_SPECIAL_NAMES_ENABLED, "false").trim());
        try {
            specialNamesChance = clampChance(Integer.parseInt(properties.getProperty(KEY_SPECIAL_NAMES_CHANCE, "100").trim()));
        } catch (NumberFormatException e) {
            LOGGER.warn("[Auto Name Tag] {} is not a number; using 100", KEY_SPECIAL_NAMES_CHANCE);
            specialNamesChance = 100;
        }
        return true;
    }

    /** 現在の設定をファイルに書き出す。 */
    public static void save() throws IOException {
        String content =
            "# Auto Name Tag - 動作設定 (Settings)\n" +
            "# 編集後はゲーム内で /animalnamer reload を実行すると反映されます\n" +
            "# （どうぶつ名鑑の設定ページやコマンドでも変更できます）\n" +
            "# After editing, run /animalnamer reload in game to apply.\n" +
            "# (You can also change these from the Animal Guide or with commands.)\n" +
            "\n" +
            "# true にすると、名前を頭上に常に表示します\n" +
            "# false の場合は、カーソルを合わせたときだけ表示されます\n" +
            "# ※変更後に名前が付いたMOBから適用されます\n" +
            "# true: always show names above mobs. false: only when you look at them.\n" +
            "# Applies to mobs named after the change.\n" +
            KEY_SHOW_NAME_ALWAYS + "=" + showNameAlways + "\n" +
            "\n" +
            "# true にすると、names.txt に書いていなくても、すべてのMOBに\n" +
            "# 特別な効果のある名前を付けます\n" +
            "#   Dinnerbone / Grumm : 上下逆さまになる（全MOB）\n" +
            "#   jeb_               : 毛が虹色になる（ヒツジ）\n" +
            "#   Toast              : 特別な模様になる（ウサギ）\n" +
            "#   Johnny             : 他のMOBを襲うようになる（ヴィンディケーター）\n" +
            "# true: also give vanilla's special-effect names to every mob, even ones not in names.txt.\n" +
            "#   Dinnerbone / Grumm : upside down (any mob)\n" +
            "#   jeb_               : rainbow wool (sheep)\n" +
            "#   Toast              : special pattern (rabbit)\n" +
            "#   Johnny             : attacks other mobs (vindicator)\n" +
            KEY_SPECIAL_NAMES_ENABLED + "=" + specialNamesEnabled + "\n" +
            "\n" +
            "# 特別な名前が付く確率（0〜100 %）\n" +
            "# 100 なら必ず特別な名前、それ未満なら外れたときに names.txt の名前が付きます\n" +
            "# Chance of a special name (0-100 %). Below 100, a miss falls back to names.txt.\n" +
            KEY_SPECIAL_NAMES_CHANCE + "=" + specialNamesChance + "\n";

        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, content, StandardCharsets.UTF_8);
    }

    public static boolean isShowNameAlways() {
        return showNameAlways;
    }

    public static void setShowNameAlways(boolean value) {
        showNameAlways = value;
    }

    public static boolean isSpecialNamesEnabled() {
        return specialNamesEnabled;
    }

    public static void setSpecialNamesEnabled(boolean value) {
        specialNamesEnabled = value;
    }

    public static int getSpecialNamesChance() {
        return specialNamesChance;
    }

    public static void setSpecialNamesChance(int value) {
        specialNamesChance = clampChance(value);
    }

    private static int clampChance(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
