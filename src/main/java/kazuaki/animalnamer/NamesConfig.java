package kazuaki.animalnamer;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * names.txt を読み込んでMOBごとの名前リストを管理するクラス。
 *
 * ファイル形式:
 *   [sheep] # ヒツジ
 *   わため
 *   こまま
 *
 * [エンティティID] の後ろに # コメント を書いてもOK。
 * # で始まる行はコメント、空行は無視。
 * 他MODのMOBは [modid:entity_id] の形式で指定できる。
 */
public final class NamesConfig {

    // 以前の版の names.txt に項目がないバニラのMOB
    private static final Set<String> NEWER_VANILLA_MOBS = Set.of("creaking");

    private static final Logger LOGGER = LoggerFactory.getLogger(AnimalNamerMod.MOD_ID);
    private static final String DEFAULT_RESOURCE = "/animalnamer_default_names.txt";
    private static final String DEFAULT_RESOURCE_EN = "/animalnamer_default_names_en.txt";
    // 設定ファイルは見る人の言語を選べないので、見出しは日本語と英語を併記する
    private static final String MOD_SECTION_MARKER = "他のMODのMOB";
    private static final String MOD_SECTION_TITLE = "# " + MOD_SECTION_MARKER
        + "（起動時に自動で追記されます） / Mobs from other mods (added automatically)";
    public static final int MAX_NAME_LENGTH = 32;
    // メモ帳などで Shift_JIS 保存された場合の読み込み用
    private static final Charset FALLBACK_CHARSET = Charset.forName("windows-31j");

    // チャンク生成スレッドからも参照されるため、差し替えは丸ごと行う
    private static volatile Map<String, List<String>> nameMap = Map.of();
    // [ID] はあるが名前が1つも書かれていないもの（names.txt に書かれた順）
    private static volatile List<String> unnamedKeys = List.of();
    private static Path configPath;

    private NamesConfig() {
    }

    public static void load() {
        // modsフォルダ内の names.txt を使用
        configPath = FabricLoader.getInstance().getGameDir()
                .resolve("mods")
                .resolve("names.txt");

        if (!Files.exists(configPath)) {
            createDefaultConfig();
        }

        reload();
    }

    /**
     * names.txt を読み直す。失敗した場合は読み込み済みの内容を維持する。
     *
     * @return 読み込みに成功したら true
     */
    public static boolean reload() {
        Map<String, List<String>> newMap = new LinkedHashMap<>();

        try {
            List<String> currentNames = null;

            for (String line : readText().lines().toList()) {
                String trimmed = line.trim();

                // 空行・コメント行をスキップ
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // [エンティティID] または [エンティティID] # 日本語名 の形式
                if (trimmed.startsWith("[")) {
                    String key = sectionKey(trimmed);
                    if (key == null) {
                        LOGGER.warn("[Auto Name Tag] Ignored a line with an unclosed ]: {}", trimmed);
                        currentNames = null;
                        continue;
                    }
                    currentNames = newMap.computeIfAbsent(key, k -> new ArrayList<>());
                } else if (currentNames != null) {
                    currentNames.add(trimmed);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[Auto Name Tag] Failed to read names.txt: {}", e.getMessage());
            return false;
        }

        unnamedKeys = newMap.entrySet().stream()
            .filter(entry -> entry.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .toList();
        newMap.values().removeIf(List::isEmpty);
        newMap.replaceAll((key, names) -> List.copyOf(names));
        // names.txt に書かれた順番を保つ（どうぶつ名鑑のページ順に使う）
        nameMap = Collections.unmodifiableMap(newMap);
        LOGGER.info("[Auto Name Tag] Loaded names.txt ({} mob types with names)", nameMap.size());
        return true;
    }

    public static String getRandomName(EntityType<?> entityType) {
        List<String> names = getNames(entityType);
        if (names.isEmpty()) {
            return null;
        }
        return names.get(ThreadLocalRandom.current().nextInt(names.size()));
    }

    /** 指定したMOBに設定されている名前の一覧。未設定なら空のリスト。 */
    public static List<String> getNames(EntityType<?> entityType) {
        Map<String, List<String>> map = nameMap;
        Identifier id = Registries.ENTITY_TYPE.getId(entityType);

        // [modid:entity_id] の指定を優先し、なければ [entity_id] を見る
        List<String> names = map.get(id.toString());
        if (names == null) {
            names = map.get(id.getPath());
        }
        return names == null ? List.of() : names;
    }

    /** names.txt に [ID] だけあって、名前がまだ書かれていないもの。 */
    public static List<String> getUnnamedKeys() {
        return unnamedKeys;
    }

    /** 名前が1つ以上設定されているMOBの一覧（キーは names.txt に書かれたID）。 */
    public static Map<String, List<String>> getAllNames() {
        return nameMap;
    }

    /**
     * names.txt に名前を追記して読み直す。コメントや他の行はそのまま残す。
     *
     * @return 追加したら true、すでに同じ名前があれば false
     */
    public static boolean addName(EntityType<?> entityType, String name) throws IOException {
        if (getNames(entityType).contains(name)) {
            return false;
        }

        String text = readText();
        List<String> lines = new ArrayList<>(text.lines().toList());
        Identifier id = Registries.ENTITY_TYPE.getId(entityType);

        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (matchesSection(lines.get(i), id)) {
                header = i;
                break;
            }
        }

        if (header == -1) {
            // セクションがなければ末尾に新しく作る
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            String key = Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
            lines.add("[" + key + "]");
            lines.add(name);
        } else {
            // セクション内の最後の名前の直後に入れる
            int insertAt = header + 1;
            for (int i = header + 1; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith("[")) {
                    break;
                }
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    insertAt = i + 1;
                }
            }
            lines.add(insertAt, name);
        }

        writeLines(lines, text);
        reload();
        return true;
    }

    /**
     * names.txt から名前を削除して読み直す。
     *
     * @return 削除したら true、該当する名前がなければ false
     */
    public static boolean removeName(EntityType<?> entityType, String name) throws IOException {
        String text = readText();
        List<String> lines = new ArrayList<>(text.lines().toList());
        Identifier id = Registries.ENTITY_TYPE.getId(entityType);

        boolean inSection = false;
        boolean removed = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("[")) {
                inSection = matchesSection(trimmed, id);
            } else if (inSection && trimmed.equals(name)) {
                lines.remove(i--);
                removed = true;
            }
        }

        if (removed) {
            writeLines(lines, text);
            reload();
        }
        return removed;
    }

    /**
     * names.txt から、そのMOBの名前をすべて削除して読み直す。[ID] の行とコメントは残す。
     *
     * @return 1つでも削除したら true
     */
    public static boolean removeAllNames(EntityType<?> entityType) throws IOException {
        String text = readText();
        List<String> lines = new ArrayList<>(text.lines().toList());
        Identifier id = Registries.ENTITY_TYPE.getId(entityType);

        boolean inSection = false;
        boolean removed = false;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("[")) {
                inSection = matchesSection(trimmed, id);
            } else if (inSection && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                lines.remove(i--);
                removed = true;
            }
        }

        if (removed) {
            writeLines(lines, text);
            reload();
        }
        return removed;
    }

    /**
     * 他のMODが追加したMOBのうち、names.txt にまだ [ID] が無いものを、名前は空欄のまま末尾に追記する。
     * すべてのMODの登録が終わってから（サーバー起動時に）呼ぶこと。
     *
     * @return 追記した種類数
     */
    public static int addMissingModSections() throws IOException {
        Set<String> known = new HashSet<>(nameMap.keySet());
        known.addAll(unnamedKeys);

        List<String> headers = new ArrayList<>();
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            // 能力値（体力など）を持つ＝生き物。矢や乗り物などは対象外
            if (!isAddable(id, type)) {
                continue;
            }
            if (known.contains(id.toString()) || known.contains(id.getPath())) {
                continue;
            }
            String displayName = type.getName().getString();
            boolean translated = !displayName.equals(type.getTranslationKey());
            headers.add("[" + id + "]" + (translated ? " # " + displayName : ""));
        }
        if (headers.isEmpty()) {
            return 0;
        }

        String text = readText();
        List<String> lines = new ArrayList<>(text.lines().toList());
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        if (!text.contains(MOD_SECTION_MARKER)) {
            lines.add("");
            lines.add("# ============================================");
            lines.add(MOD_SECTION_TITLE);
            lines.add("# ============================================");
        }
        for (String header : headers) {
            lines.add("");
            lines.add(header);
        }

        writeLines(lines, text);
        reload();
        LOGGER.info("[Auto Name Tag] Added {} mob types from other mods to names.txt", headers.size());
        return headers.size();
    }

    /** names.txt に自動で追記するMOB。他のMODの生き物と、古い names.txt にはないバニラの新しいMOB。 */
    private static boolean isAddable(Identifier id, EntityType<?> type) {
        if (Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
            return NEWER_VANILLA_MOBS.contains(id.getPath());
        }
        return DefaultAttributeRegistry.hasDefinitionFor(type);
    }

    /** 名前として使える文字列か（空・長すぎ・# や [ で始まるものは不可）。 */
    public static boolean isValidName(String name) {
        return !name.isBlank() && name.length() <= MAX_NAME_LENGTH && !name.startsWith("#") && !name.startsWith("[");
    }

    public static Path getConfigPath() {
        return configPath;
    }

    /** "[sheep] # ヒツジ" から "sheep" を取り出す。セクション行でなければ null。 */
    private static String sectionKey(String line) {
        String trimmed = line.trim();
        int closeBracket = trimmed.indexOf(']');
        if (!trimmed.startsWith("[") || closeBracket == -1) {
            return null;
        }
        return trimmed.substring(1, closeBracket).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesSection(String line, Identifier id) {
        String key = sectionKey(line);
        return key != null && (key.equals(id.toString()) || key.equals(id.getPath()));
    }

    private static String readText() throws IOException {
        byte[] bytes = Files.readAllBytes(configPath);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            LOGGER.warn("[Auto Name Tag] names.txt is not UTF-8; reading it as Shift_JIS");
            text = new String(bytes, FALLBACK_CHARSET);
        }
        // メモ帳が付ける BOM を取り除く
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    private static void writeLines(List<String> lines, String originalText) throws IOException {
        // 元の改行コードに合わせる
        String separator = originalText.contains("\r\n") ? "\r\n" : "\n";
        Files.writeString(configPath, String.join(separator, lines) + separator, StandardCharsets.UTF_8);
    }

    private static void createDefaultConfig() {
        // 最初の名前リストは、パソコンの言語が日本語なら日本語版、それ以外は英語版を使う
        String resource = "ja".equals(Locale.getDefault().getLanguage()) ? DEFAULT_RESOURCE : DEFAULT_RESOURCE_EN;
        try (InputStream in = NamesConfig.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Default name list not found in the jar: " + resource);
            }
            Files.createDirectories(configPath.getParent());
            Files.copy(in, configPath);
            LOGGER.info("[Auto Name Tag] Created the default names.txt: {}", configPath);
        } catch (IOException e) {
            LOGGER.error("[Auto Name Tag] Failed to create names.txt: {}", e.getMessage());
        }
    }
}
