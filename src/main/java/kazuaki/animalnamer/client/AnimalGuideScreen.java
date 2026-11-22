package kazuaki.animalnamer.client;

import kazuaki.animalnamer.AnimalNamerMod;
import kazuaki.animalnamer.NamesConfig;
import kazuaki.animalnamer.network.EditNamesPayload;
import kazuaki.animalnamer.network.OpenGuidePayload;
import kazuaki.animalnamer.network.UpdateSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * どうぶつ名鑑の画面。
 * 表紙と目次のあと、見開きの左ページにMOBの姿、右ページにそのMOBに付く名前を表示する。
 * そのあとに、まだ名前のないMOBの一覧、コマンド一覧、設定のページが続く。
 * 編集できる人（OP・ワールドの持ち主）は、名前の追加・削除とページの追加・削除もできる。
 */
public class AnimalGuideScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 180;
    private static final int PAGE_WIDTH = PANEL_WIDTH / 2;
    private static final int MARGIN = 12;
    private static final int TEXT_WIDTH = PAGE_WIDTH - MARGIN * 2;
    private static final int LINE_HEIGHT = 11;
    private static final int NAMES_TOP = 28;
    // 編集欄を出すぶん、編集できる人は一覧が短くなる
    private static final int VISIBLE_NAMES = 12;
    private static final int VISIBLE_NAMES_EDITING = 10;
    private static final int REMOVE_MARK_WIDTH = 10;

    private static final int COLOR_COVER = 0xFF4C863A;
    private static final int COLOR_COVER_EDGE = 0xFF336029;
    private static final int COLOR_PAPER = 0xFFF6ECCF;
    private static final int COLOR_PAPER_SHADE = 0xFFE6D8AE;
    private static final int COLOR_INK = 0xFF3B2A14;
    private static final int COLOR_INK_LIGHT = 0xFF7A6540;
    private static final int COLOR_WARNING = 0xFFB03020;
    private static final int COLOR_GOLD = 0xFFF2D675;
    private static final int COLOR_TOGGLE_OFF = 0xFF6B5A44;
    private static final int COLOR_NAME_TAG = 0x80000000;

    // 画面の文字は言語ファイル（assets/animalnamer/lang）から取る
    private static final String KEY = "gui." + AnimalNamerMod.MOD_ID + ".";
    private static final int SETTINGS_HELP_LINES = 6;

    /** コマンド一覧の1行。ボタンを押すと input がチャット欄に入る。表示名と説明は言語ファイルの id で引く。 */
    private record Command(String id, String input) {
    }

    private static final String COMMAND_ROOT = "/" + AnimalNamerMod.MOD_ID + " ";
    private static final List<Command> COMMANDS = List.of(
        new Command("add", "add "),
        new Command("remove", "remove "),
        new Command("list", "list"),
        new Command("nameall", "nameall"),
        new Command("showname", "showname "),
        new Command("special", "special "),
        new Command("chance", "special chance "),
        new Command("reload", "reload")
    );
    private static final int COMMANDS_PER_PAGE = 4;
    private static final int COMMAND_ROW_HEIGHT = 36;

    // ページの並び: 表紙 → 目次 → MOBのページ → まだ名前のないMOB（あれば）→ コマンド一覧 → 設定
    private static final int COVER_PAGE = 0;
    private static final int CONTENTS_PAGE = 1;
    private static final int FIRST_MOB_PAGE = 2;

    private static final int COVER_WIDTH = 180;
    private static final int SPINE_WIDTH = 8;

    // 目次: 左右のページに8件ずつ
    private static final int CONTENTS_PER_PAGE = 8;
    private static final int CONTENTS_ROW_HEIGHT = 15;
    private static final int CONTENTS_TOP = 26;
    private static final int CONTENTS_ICON_WIDTH = 14;
    private static final float CONTENTS_ICON_SCALE = 0.75F;

    // まだ名前のないMOBの一覧: 1件を2行（MOB名・ID）で、左右のページに7件ずつ
    private static final int UNNAMED_PER_PAGE = 7;
    private static final int UNNAMED_ROW_HEIGHT = 19;
    private static final int UNNAMED_TOP = 26;
    private static final int ADD_MARK_WIDTH = 14;

    /** 目次の1行。page は開くページ。まだページがないMOB（検索で見つかったもの）は -1。 */
    private record ContentsRow(Identifier id, int nameCount, int page) {
    }

    private List<OpenGuidePayload.Entry> entries;
    private List<Identifier> unnamed;
    private boolean canEdit;
    private boolean showNameAlways;
    private boolean specialNamesEnabled;
    private int specialNamesChance;
    // サーバーへまだ送っていない設定の変更があるか
    private boolean dirty;

    // 名前のないMOBから「ページを追加」で開いた、まだ名前が1つもない仮のページ
    private Identifier draft;
    // MOBのページ（entries の後ろに、あれば仮のページ）
    private List<OpenGuidePayload.Entry> mobPages = List.of();
    private List<ContentsRow> contentsRows = List.of();

    private int page = COVER_PAGE;
    private int scroll;
    private String pendingName = "";
    private String search = "";
    private boolean confirmDelete;
    private Identifier coverMob;
    private String coverName = "";
    private final Map<Identifier, Optional<LivingEntity>> entityCache = new HashMap<>();
    private final Map<Identifier, ItemStack> iconCache = new HashMap<>();

    private int left;
    private int top;
    private ButtonWidget contentsButton;
    private OnOffToggle showNameToggle;
    private OnOffToggle specialToggle;
    private ChanceSlider chanceSlider;
    private final List<ButtonWidget> commandButtons = new ArrayList<>();
    private final List<ButtonWidget> contentsLinks = new ArrayList<>();
    private TextFieldWidget searchField;
    private TextFieldWidget nameField;
    private ButtonWidget addNameButton;
    private ButtonWidget deletePageButton;
    private ButtonWidget unnamedLink;

    public AnimalGuideScreen(OpenGuidePayload payload) {
        super(Text.translatable("item.animalnamer.animal_guide"));
        applyPayload(payload);
        chooseCoverMob();
    }

    private void applyPayload(OpenGuidePayload payload) {
        entries = payload.entries();
        unnamed = payload.unnamed();
        canEdit = payload.canEdit();
        if (!dirty) {
            showNameAlways = payload.showNameAlways();
            specialNamesEnabled = payload.specialNamesEnabled();
            specialNamesChance = payload.specialNamesChance();
        }
        rebuildMobPages();
        rebuildContents();
    }

    private void rebuildMobPages() {
        List<OpenGuidePayload.Entry> pages = new ArrayList<>(entries);
        if (draft != null) {
            pages.add(new OpenGuidePayload.Entry(draft, List.of()));
        }
        mobPages = pages;
    }

    /** 検索欄の内容に合う目次を作る。検索のときは、まだページのないMOBも編集できる人に見せる。 */
    private void rebuildContents() {
        String query = normalize(search.trim());
        List<ContentsRow> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            OpenGuidePayload.Entry entry = entries.get(i);
            if (query.isEmpty() || matchesSearch(entry.entityId(), entry.names(), query)) {
                rows.add(new ContentsRow(entry.entityId(), entry.names().size(), FIRST_MOB_PAGE + i));
            }
        }
        if (canEdit && !query.isEmpty()) {
            for (Identifier id : unnamed) {
                if (matchesSearch(id, List.of(), query)) {
                    rows.add(new ContentsRow(id, 0, -1));
                }
            }
        }
        contentsRows = rows;
    }

    private static boolean matchesSearch(Identifier id, List<String> names, String query) {
        if (normalize(mobName(id)).contains(query) || normalize(id.toString()).contains(query)) {
            return true;
        }
        return names.stream().anyMatch(name -> normalize(name).contains(query));
    }

    /** 大文字と小文字、ひらがなとカタカナの違いを無視して探せるようにそろえる。 */
    private static String normalize(String value) {
        StringBuilder result = new StringBuilder(value.toLowerCase(Locale.ROOT));
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c >= 'ァ' && c <= 'ヶ') {
                result.setCharAt(i, (char) (c - 0x60));
            }
        }
        return result.toString();
    }

    /** 表紙に飾るMOBと名前を、開くたびに選ぶ。 */
    private void chooseCoverMob() {
        if (entries.isEmpty()) {
            coverMob = Identifier.ofVanilla("sheep");
            return;
        }
        OpenGuidePayload.Entry entry = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        coverMob = entry.entityId();
        coverName = entry.names().get(ThreadLocalRandom.current().nextInt(entry.names().size()));
    }

    /** 名前の追加・削除のあと、サーバーから届いた最新の内容に差し替える。見ていたページはできるだけ保つ。 */
    public void update(OpenGuidePayload payload) {
        Identifier viewing = isMobPage() ? currentEntry().entityId() : null;
        boolean onContents = isContentsPage();
        boolean onUnnamed = isUnnamedPage();
        boolean onCommand = isCommandPage();
        boolean onSettings = isSettingsPage();

        applyPayload(payload);

        if (viewing != null) {
            int index = indexOfEntry(viewing);
            if (index != -1) {
                // 名前が付いたので、仮のページは本物のページになった
                if (viewing.equals(draft)) {
                    draft = null;
                    rebuildMobPages();
                }
                page = FIRST_MOB_PAGE + index;
                scroll = MathHelper.clamp(scroll, 0, maxNameScroll());
            } else if (viewing.equals(draft)) {
                page = unnamedPageIndex() - 1;
            } else {
                // ページごと削除された: 名前のないMOBの一覧へ
                page = Math.min(unnamedPageIndex(), pageCount() - 1);
                scroll = 0;
                pendingName = "";
            }
        } else if (onContents) {
            scroll = Math.min(scroll, (contentsStepCount() - 1) * CONTENTS_PER_PAGE);
        } else if (onUnnamed) {
            page = unnamed.isEmpty() ? commandPageIndex() : unnamedPageIndex();
            scroll = Math.min(scroll, (unnamedStepCount() - 1) * UNNAMED_PER_PAGE);
        } else if (onCommand) {
            page = commandPageIndex();
        } else if (onSettings) {
            page = commandPageIndex() + 1;
        }

        confirmDelete = false;
        // 続けて名前を入力できるよう、入力欄を選んだままにする
        boolean typing = nameField != null && nameField.isFocused();
        clearAndInit();
        if (typing && nameField.visible) {
            setFocused(nameField);
        }
    }

    private int indexOfEntry(Identifier id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).entityId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT - 28) / 2;
        int buttonY = top + PANEL_HEIGHT + 8;

        addDrawableChild(ButtonWidget.builder(text("prev"), button -> turnPage(-1))
            .dimensions(left, buttonY, 60, 20).build());
        contentsButton = addDrawableChild(ButtonWidget.builder(text("contents"), button -> goTo(CONTENTS_PAGE))
            .dimensions(left + 80, buttonY, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(text("close"), button -> close())
            .dimensions(left + 160, buttonY, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(text("next"), button -> turnPage(1))
            .dimensions(left + PANEL_WIDTH - 60, buttonY, 60, 20).build());

        int rightX = left + PAGE_WIDTH + MARGIN;
        showNameToggle = addDrawableChild(new OnOffToggle(rightX, top + 27, TEXT_WIDTH, 20,
            "settings.show_name", () -> showNameAlways, () -> {
                showNameAlways = !showNameAlways;
                settingsChanged();
            }));
        specialToggle = addDrawableChild(new OnOffToggle(rightX, top + 67, TEXT_WIDTH, 20,
            "settings.special", () -> specialNamesEnabled, () -> {
                specialNamesEnabled = !specialNamesEnabled;
                settingsChanged();
            }));
        chanceSlider = addDrawableChild(new ChanceSlider(rightX, top + 107, TEXT_WIDTH, 20));
        chanceSlider.setTooltip(Tooltip.of(text("settings.chance.tooltip")));

        // コマンド一覧: 左右のページに4つずつ。押すとチャット欄にコマンドが入る
        commandButtons.clear();
        for (int i = 0; i < COMMANDS.size(); i++) {
            Command command = COMMANDS.get(i);
            int x = left + (i / COMMANDS_PER_PAGE) * PAGE_WIDTH + MARGIN;
            int y = top + 35 + (i % COMMANDS_PER_PAGE) * COMMAND_ROW_HEIGHT;
            commandButtons.add(addDrawableChild(ButtonWidget.builder(text("command." + command.id() + ".label"),
                    button -> openChat(command.input()))
                .dimensions(x, y, TEXT_WIDTH, 20).build()));
        }

        // 目次: 検索欄と、一覧の下の、MOB以外のページへのボタン
        searchField = addDrawableChild(new TextFieldWidget(textRenderer, rightX, top + 6, TEXT_WIDTH, 16,
            text("search_field")));
        searchField.setPlaceholder(text("search_placeholder"));
        searchField.setText(search);
        searchField.setChangedListener(text -> {
            search = text;
            scroll = 0;
            rebuildContents();
        });

        int linkWidth = 88;
        int linkGap = (PANEL_WIDTH - MARGIN * 2 - linkWidth * 3) / 2;
        int linkY = top + 148;
        contentsLinks.clear();
        unnamedLink = addContentsLink("contents.unnamed", left + MARGIN, linkY, linkWidth,
            button -> goTo(unnamedPageIndex()));
        addContentsLink("contents.commands", left + MARGIN + linkWidth + linkGap, linkY, linkWidth,
            button -> goTo(commandPageIndex()));
        addContentsLink("contents.settings", left + MARGIN + (linkWidth + linkGap) * 2, linkY, linkWidth,
            button -> goTo(commandPageIndex() + 1));

        // MOBのページの編集欄: 名前の入力・追加と、ページの削除
        int editY = top + NAMES_TOP + VISIBLE_NAMES_EDITING * LINE_HEIGHT + 4;
        addNameButton = addDrawableChild(ButtonWidget.builder(text("add"), button -> submitName())
            .dimensions(rightX + TEXT_WIDTH - 36, editY - 1, 36, 20).build());
        nameField = addDrawableChild(new TextFieldWidget(textRenderer, rightX, editY, TEXT_WIDTH - 40, 18,
            text("name_field")));
        nameField.setMaxLength(NamesConfig.MAX_NAME_LENGTH);
        nameField.setPlaceholder(text("name_placeholder"));
        nameField.setText(pendingName);
        nameField.setChangedListener(text -> {
            pendingName = text;
            addNameButton.active = canSubmitName();
        });
        deletePageButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> deletePage())
            .tooltip(Tooltip.of(text("tooltip.delete_page")))
            .dimensions(rightX + TEXT_WIDTH - 58, top + 7, 58, 14).build());

        updateWidgets();
    }

    private ButtonWidget addContentsLink(String key, int x, int y, int linkWidth, ButtonWidget.PressAction action) {
        ButtonWidget button = addDrawableChild(ButtonWidget.builder(text(key), action)
            .dimensions(x, y, linkWidth, 14).build());
        contentsLinks.add(button);
        return button;
    }

    private int unnamedPageIndex() {
        return FIRST_MOB_PAGE + mobPages.size();
    }

    private int commandPageIndex() {
        return unnamedPageIndex() + (unnamed.isEmpty() ? 0 : 1);
    }

    private int pageCount() {
        return commandPageIndex() + 2;
    }

    private boolean isCoverPage() {
        return page == COVER_PAGE;
    }

    private boolean isContentsPage() {
        return page == CONTENTS_PAGE;
    }

    private boolean isMobPage() {
        return page >= FIRST_MOB_PAGE && page < unnamedPageIndex();
    }

    private OpenGuidePayload.Entry currentEntry() {
        return mobPages.get(page - FIRST_MOB_PAGE);
    }

    private boolean isDraftPage() {
        return draft != null && page == unnamedPageIndex() - 1;
    }

    private boolean isUnnamedPage() {
        return !unnamed.isEmpty() && page == unnamedPageIndex();
    }

    private boolean isCommandPage() {
        return page == commandPageIndex();
    }

    private boolean isSettingsPage() {
        return page == commandPageIndex() + 1;
    }

    private boolean canUseCommands() {
        return client != null && client.player != null && client.player.hasPermissionLevel(2);
    }

    private int visibleNames() {
        return canEdit ? VISIBLE_NAMES_EDITING : VISIBLE_NAMES;
    }

    private int maxNameScroll() {
        return isMobPage() ? Math.max(0, currentEntry().names().size() - visibleNames()) : 0;
    }

    private void turnPage(int delta) {
        goTo(page + delta);
    }

    private void goTo(int target) {
        Identifier viewing = isMobPage() ? currentEntry().entityId() : null;
        // 名前を付けないまま仮のページを離れたら、そのページは片付ける
        if (isDraftPage()) {
            draft = null;
            rebuildMobPages();
            if (target > page) {
                target--;
            }
        }
        // 最初と最後のページをつなげる
        page = Math.floorMod(target, pageCount());
        // 前の検索が残っていると一部のMOBしかいないように見えるので、目次は全件に戻して開く
        if (isContentsPage()) {
            searchField.setText("");
        }
        scroll = isContentsPage() ? contentsScrollFor(viewing) : 0;
        confirmDelete = false;
        pendingName = "";
        nameField.setText("");
        setFocused(null);
        updateWidgets();
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F));
        }
    }

    /** 目次に戻ったとき、さっきまで見ていたMOBの行が見える位置。 */
    private int contentsScrollFor(Identifier id) {
        for (int i = 0; i < contentsRows.size(); i++) {
            if (contentsRows.get(i).id().equals(id)) {
                return Math.min(i / CONTENTS_PER_PAGE, contentsStepCount() - 1) * CONTENTS_PER_PAGE;
            }
        }
        return 0;
    }

    private void updateWidgets() {
        boolean settings = isSettingsPage();
        boolean commands = isCommandPage();
        boolean commandsUsable = canUseCommands();
        for (ButtonWidget button : commandButtons) {
            button.visible = commands;
            button.active = commandsUsable;
        }

        boolean contents = isContentsPage();
        contentsButton.active = !contents;
        searchField.visible = contents;
        for (ButtonWidget link : contentsLinks) {
            link.visible = contents;
        }
        unnamedLink.visible = contents && !unnamed.isEmpty();

        showNameToggle.visible = settings;
        specialToggle.visible = settings;
        chanceSlider.visible = settings;
        showNameToggle.active = canEdit;
        specialToggle.active = canEdit;
        // 確率は、特別な名前がオンのときだけ意味がある
        chanceSlider.active = canEdit && specialNamesEnabled;

        boolean editing = isMobPage() && canEdit;
        nameField.visible = editing;
        addNameButton.visible = editing;
        addNameButton.active = canSubmitName();
        deletePageButton.visible = editing && !currentEntry().names().isEmpty();
        deletePageButton.setMessage(text(confirmDelete ? "confirm" : "delete_page"));
        if (!editing) {
            nameField.setFocused(false);
        }
    }

    private static Text onOffText(boolean enabled) {
        return text(enabled ? "on" : "off");
    }

    private static Text text(String key, Object... args) {
        return Text.translatable(KEY + key, args);
    }

    private static String tr(String key, Object... args) {
        return I18n.translate(KEY + key, args);
    }

    private static String mobName(Identifier id) {
        return Registries.ENTITY_TYPE.getOptionalValue(id).map(type -> type.getName().getString()).orElse(id.toString());
    }

    private void openChat(String commandInput) {
        client.setScreen(new ChatScreen(COMMAND_ROOT + commandInput));
    }

    private void settingsChanged() {
        dirty = true;
        updateWidgets();
        sendSettings();
    }

    private void sendSettings() {
        if (!dirty || !canEdit) {
            return;
        }
        dirty = false;
        ClientPlayNetworking.send(new UpdateSettingsPayload(showNameAlways, specialNamesEnabled, specialNamesChance));
    }

    // ---- 名前とページの編集 ----

    private void sendEdit(EditNamesPayload.Action action, String name) {
        if (canEdit && isMobPage()) {
            ClientPlayNetworking.send(new EditNamesPayload(action, currentEntry().entityId(), name));
        }
    }

    /** 入力中の名前を追加できない理由（言語ファイルのキー）。問題がなければ null。 */
    private String nameProblem() {
        String name = pendingName.trim();
        if (name.isEmpty() || !isMobPage()) {
            return null;
        }
        if (!NamesConfig.isValidName(name)) {
            return "mob.invalid";
        }
        return currentEntry().names().contains(name) ? "mob.duplicate" : null;
    }

    private boolean canSubmitName() {
        return isMobPage() && !pendingName.isBlank() && nameProblem() == null;
    }

    private void submitName() {
        if (!canSubmitName()) {
            return;
        }
        sendEdit(EditNamesPayload.Action.ADD, pendingName.trim());
        pendingName = "";
        nameField.setText("");
    }

    private void removeName(String name) {
        // 最後の1つを消してもページを離れず、その場で戻せるよう仮のページとして残す
        if (currentEntry().names().size() == 1) {
            draft = currentEntry().entityId();
        }
        sendEdit(EditNamesPayload.Action.REMOVE, name);
        // 押し間違えても「追加」で戻せるよう、消した名前を入力欄に残す
        if (!nameField.isFocused()) {
            nameField.setText(name);
        }
    }

    // 押し間違いで消えないよう、1回目は確認に切り替えるだけ
    private void deletePage() {
        if (!confirmDelete) {
            confirmDelete = true;
            updateWidgets();
            return;
        }
        confirmDelete = false;
        sendEdit(EditNamesPayload.Action.CLEAR, "");
    }

    /** 名前のないMOBの一覧や検索結果から、そのMOBの仮のページを作って開く。 */
    private void openDraft(Identifier id) {
        draft = id;
        rebuildMobPages();
        page = unnamedPageIndex() - 1;
        scroll = 0;
        pendingName = "";
        confirmDelete = false;
        clearAndInit();
        setFocused(nameField);
    }

    private void openContentsRow(ContentsRow row) {
        if (row.page() != -1) {
            goTo(row.page());
        } else if (canEdit) {
            openDraft(row.id());
        }
    }

    @Override
    public void removed() {
        sendSettings();
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- マウス・キー操作 ----

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && nameField.isFocused()) {
            submitName();
            return true;
        }
        if (enter && searchField.isFocused() && !contentsRows.isEmpty()) {
            openContentsRow(contentsRows.get(0));
            return true;
        }
        // 文字入力やスライダーの操作中でなければ、矢印キーでページをめくる
        if (!(getFocused() instanceof TextFieldWidget) && !(getFocused() instanceof SliderWidget)) {
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                turnPage(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                turnPage(1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        setFocused(null);
        if (button != 0) {
            return false;
        }

        if (isCoverPage()) {
            if (isOnCover(mouseX, mouseY)) {
                goTo(CONTENTS_PAGE);
                return true;
            }
        } else if (isContentsPage()) {
            int index = contentsIndexAt(mouseX, mouseY);
            if (index != -1) {
                openContentsRow(contentsRows.get(index));
                return true;
            }
        } else if (isMobPage()) {
            int index = removeMarkAt(mouseX, mouseY);
            if (index != -1) {
                removeName(currentEntry().names().get(index));
                return true;
            }
        } else if (isUnnamedPage()) {
            int index = unnamedIndexAt(mouseX, mouseY);
            if (index != -1) {
                Identifier id = unnamed.get(index);
                if (canEdit && isOnAddMark(mouseX)) {
                    openDraft(id);
                } else if (canUseCommands()) {
                    openChat("add " + shortId(id) + " ");
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMobPage()) {
            scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount), 0, maxNameScroll());
            return true;
        }
        // 一覧は1列ずつ送る
        if (isContentsPage()) {
            scroll = nextColumn(verticalAmount, CONTENTS_PER_PAGE, contentsStepCount());
            return true;
        }
        if (isUnnamedPage()) {
            scroll = nextColumn(verticalAmount, UNNAMED_PER_PAGE, unnamedStepCount());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int nextColumn(double verticalAmount, int perPage, int stepCount) {
        int step = MathHelper.clamp(scroll / perPage - (int) Math.signum(verticalAmount), 0, stepCount - 1);
        return step * perPage;
    }

    private boolean isOnCover(double mouseX, double mouseY) {
        int coverLeft = coverLeft();
        return mouseX >= coverLeft && mouseX < coverLeft + COVER_WIDTH && mouseY >= top && mouseY < top + PANEL_HEIGHT;
    }

    private int coverLeft() {
        return left + (PANEL_WIDTH - COVER_WIDTH) / 2;
    }

    /** マウスが名前の一覧の行に乗っていれば、その名前の番号。乗っていなければ -1。 */
    private int nameIndexAt(double mouseX, double mouseY) {
        if (!isMobPage()) {
            return -1;
        }
        int nameLeft = left + PAGE_WIDTH + MARGIN;
        if (mouseX < nameLeft || mouseX >= nameLeft + TEXT_WIDTH || mouseY < top + NAMES_TOP) {
            return -1;
        }
        int index = scroll + (int) (mouseY - top - NAMES_TOP) / LINE_HEIGHT;
        int end = Math.min(currentEntry().names().size(), scroll + visibleNames());
        return index < end ? index : -1;
    }

    /** マウスが名前の右の「×」に乗っていれば、その名前の番号。乗っていなければ -1。 */
    private int removeMarkAt(double mouseX, double mouseY) {
        int right = left + PAGE_WIDTH + MARGIN + TEXT_WIDTH;
        return canEdit && mouseX >= right - REMOVE_MARK_WIDTH ? nameIndexAt(mouseX, mouseY) : -1;
    }

    /** マウスが見開きの一覧の行に乗っていれば、その番号。乗っていなければ -1。 */
    private int listIndexAt(double mouseX, double mouseY, int listTop, int rowHeight, int perPage, int size) {
        double relativeX = mouseX - left;
        double relativeY = mouseY - top - listTop;
        if (relativeX < 0 || relativeX >= PANEL_WIDTH || relativeY < 0) {
            return -1;
        }
        int column = (int) relativeX / PAGE_WIDTH;
        double inPage = relativeX - column * PAGE_WIDTH;
        int row = (int) relativeY / rowHeight;
        if (inPage < MARGIN || inPage >= MARGIN + TEXT_WIDTH || row >= perPage) {
            return -1;
        }
        int index = scroll + column * perPage + row;
        return index < size ? index : -1;
    }

    private int contentsIndexAt(double mouseX, double mouseY) {
        return listIndexAt(mouseX, mouseY, CONTENTS_TOP, CONTENTS_ROW_HEIGHT, CONTENTS_PER_PAGE, contentsRows.size());
    }

    private int unnamedIndexAt(double mouseX, double mouseY) {
        return listIndexAt(mouseX, mouseY, UNNAMED_TOP, UNNAMED_ROW_HEIGHT, UNNAMED_PER_PAGE, unnamed.size());
    }

    private boolean isOnAddMark(double mouseX) {
        double inPage = (mouseX - left) % PAGE_WIDTH;
        return inPage >= MARGIN + TEXT_WIDTH - ADD_MARK_WIDTH;
    }

    private static String shortId(Identifier id) {
        return Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    // ---- 描画 ----

    // 本の土台はここで描く（文字やMOBより先、ボタンより先に描かれる）
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        if (isCoverPage()) {
            renderCoverBackground(context);
            return;
        }

        context.fill(left - 5, top - 5, right + 5, bottom + 5, COLOR_COVER_EDGE);
        context.fill(left - 4, top - 4, right + 4, bottom + 4, COLOR_COVER);
        context.fill(left, top, right, bottom, COLOR_PAPER);
        // 見開きの中央の折り目
        context.fill(left + PAGE_WIDTH - 1, top, left + PAGE_WIDTH + 1, bottom, COLOR_PAPER_SHADE);

        // マウスを乗せた行を目立たせる
        if (isContentsPage()) {
            int index = contentsIndexAt(mouseX, mouseY);
            if (index != -1) {
                highlightRow(context, index - scroll, CONTENTS_PER_PAGE, CONTENTS_TOP, CONTENTS_ROW_HEIGHT);
            }
        } else if (isMobPage()) {
            context.fill(left + MARGIN, top + 26, left + PAGE_WIDTH - MARGIN, bottom - 26, COLOR_PAPER_SHADE);
            int index = removeMarkAt(mouseX, mouseY);
            if (index != -1) {
                int x = left + PAGE_WIDTH + MARGIN;
                int y = top + NAMES_TOP + (index - scroll) * LINE_HEIGHT;
                context.fill(x - 2, y - 1, x + TEXT_WIDTH + 2, y + LINE_HEIGHT - 1, COLOR_PAPER_SHADE);
            }
        } else if (isUnnamedPage() && (canEdit || canUseCommands())) {
            int index = unnamedIndexAt(mouseX, mouseY);
            if (index != -1) {
                highlightRow(context, index - scroll, UNNAMED_PER_PAGE, UNNAMED_TOP, UNNAMED_ROW_HEIGHT);
            }
        }
    }

    private void highlightRow(DrawContext context, int slot, int perPage, int listTop, int rowHeight) {
        int x = left + (slot / perPage) * PAGE_WIDTH + MARGIN;
        int y = top + listTop + (slot % perPage) * rowHeight;
        context.fill(x - 2, y - 1, x + TEXT_WIDTH + 2, y + rowHeight - 1, COLOR_PAPER_SHADE);
    }

    private void renderCoverBackground(DrawContext context) {
        int coverLeft = coverLeft();
        int coverRight = coverLeft + COVER_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        context.fill(coverLeft - 1, top - 5, coverRight + 1, bottom + 5, COLOR_COVER_EDGE);
        context.fill(coverLeft, top - 4, coverRight, bottom + 4, COLOR_COVER);
        // 背表紙と、金色の枠
        context.fill(coverLeft, top - 4, coverLeft + SPINE_WIDTH, bottom + 4, COLOR_COVER_EDGE);
        context.drawBorder(coverLeft + SPINE_WIDTH + 5, top + 1, COVER_WIDTH - SPINE_WIDTH - 10, PANEL_HEIGHT - 2, COLOR_GOLD);
        // MOBを飾る窓
        int center = coverCenter();
        context.fill(center - 56, top + 37, center + 56, top + 129, COLOR_GOLD);
        context.fill(center - 55, top + 38, center + 55, top + 128, COLOR_PAPER);
    }

    private int coverCenter() {
        return coverLeft() + (COVER_WIDTH + SPINE_WIDTH) / 2;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // 「本当に？」のまま離れたら、元の「ページ削除」に戻す
        if (confirmDelete && !deletePageButton.isHovered()) {
            confirmDelete = false;
            updateWidgets();
        }

        if (isCoverPage()) {
            renderCover(context, mouseX, mouseY);
        } else if (isContentsPage()) {
            renderContentsPage(context);
        } else if (isSettingsPage()) {
            renderSettingsPage(context);
        } else if (isCommandPage()) {
            renderCommandPage(context);
        } else if (isUnnamedPage()) {
            renderUnnamedPage(context);
        } else {
            renderMobPage(context, mouseX, mouseY);
        }

        // 右下: 本全体のページ。一覧のページでは、一覧の中のどこを見ているかを出す
        if (!isCoverPage() && nameProblem() == null) {
            String pageText;
            if (isContentsPage()) {
                pageText = (scroll / CONTENTS_PER_PAGE + 1) + " / " + contentsStepCount();
            } else if (isUnnamedPage()) {
                pageText = (scroll / UNNAMED_PER_PAGE + 1) + " / " + unnamedStepCount();
            } else {
                pageText = (page + 1) + " / " + pageCount();
            }
            drawInk(context, pageText, left + PANEL_WIDTH - MARGIN - textRenderer.getWidth(pageText),
                top + PANEL_HEIGHT - 14, COLOR_INK_LIGHT);
        }

        renderTooltip(context, mouseX, mouseY);
    }

    private void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        if (removeMarkAt(mouseX, mouseY) != -1) {
            context.drawTooltip(textRenderer, text("tooltip.remove_name"), mouseX, mouseY);
        } else if (nameIndexAt(mouseX, mouseY) != -1) {
            // 枠に収まらず切れている名前は、全部見せる
            String name = currentEntry().names().get(nameIndexAt(mouseX, mouseY));
            if (textRenderer.getWidth("・" + name) > nameWidth()) {
                context.drawTooltip(textRenderer, Text.literal(name), mouseX, mouseY);
            }
        } else if (isUnnamedPage() && unnamedIndexAt(mouseX, mouseY) != -1) {
            if (canEdit && isOnAddMark(mouseX)) {
                context.drawTooltip(textRenderer, text("tooltip.add_page"), mouseX, mouseY);
            } else if (canUseCommands()) {
                context.drawTooltip(textRenderer, text("tooltip.add_command"), mouseX, mouseY);
            }
        }
    }

    private void renderCover(DrawContext context, int mouseX, int mouseY) {
        int center = coverCenter();

        // 題名は2倍の大きさで
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(center - textRenderer.getWidth(title), top + 12, 0.0F);
        matrices.scale(2.0F, 2.0F, 1.0F);
        context.drawText(textRenderer, title, 0, 0, COLOR_GOLD, true);
        matrices.pop();

        LivingEntity entity = getEntity(coverMob, Registries.ENTITY_TYPE.getOptionalValue(coverMob));
        if (entity != null) {
            drawMob(context, center - 54, top + 52, center + 54, top + 127, 62.0F, 90.0F, 46, mouseX, mouseY, entity);
        }

        // 実際に登録されている名前を、名札のように出す
        if (!coverName.isEmpty()) {
            String name = textRenderer.trimToWidth(coverName, 100);
            int nameWidth = textRenderer.getWidth(name);
            context.fill(center - nameWidth / 2 - 2, top + 41, center + nameWidth / 2 + 2, top + 52, COLOR_NAME_TAG);
            context.drawText(textRenderer, name, center - nameWidth / 2, top + 43, 0xFFFFFFFF, false);
        }

        int totalNames = entries.stream().mapToInt(entry -> entry.names().size()).sum();
        context.drawCenteredTextWithShadow(textRenderer, tr("cover.stats", entries.size(), totalNames),
            center, top + 136, COLOR_PAPER);
        context.drawCenteredTextWithShadow(textRenderer, "Auto Name Tag", center, top + 149, COLOR_GOLD);
        context.drawCenteredTextWithShadow(textRenderer, tr("cover.hint"), center, top + 163, COLOR_PAPER);
    }

    private void renderContentsPage(DrawContext context) {
        drawInk(context, tr("contents.title"), left + MARGIN, top + 11, COLOR_INK);
        String count = tr("contents.count", entries.size());
        drawInk(context, count, left + PAGE_WIDTH - MARGIN - textRenderer.getWidth(count), top + 11, COLOR_INK_LIGHT);

        if (contentsRows.isEmpty()) {
            drawInk(context, tr("contents.not_found"), left + MARGIN, top + CONTENTS_TOP + 4, COLOR_INK_LIGHT);
        }

        // 左ページを上から下へ、続きを右ページへ
        int end = Math.min(contentsRows.size(), scroll + CONTENTS_PER_PAGE * 2);
        for (int i = scroll; i < end; i++) {
            ContentsRow row = contentsRows.get(i);
            int slot = i - scroll;
            int x = left + (slot / CONTENTS_PER_PAGE) * PAGE_WIDTH + MARGIN;
            int y = top + CONTENTS_TOP + (slot % CONTENTS_PER_PAGE) * CONTENTS_ROW_HEIGHT;

            drawMobIcon(context, row.id(), x, y);
            // 右端は名前の数。まだページがないMOBは、作れることを「+」で示す
            String mark = row.page() == -1 ? "+" : String.valueOf(row.nameCount());
            int markWidth = textRenderer.getWidth(mark);
            String label = textRenderer.trimToWidth(mobName(row.id()), TEXT_WIDTH - CONTENTS_ICON_WIDTH - markWidth - 4);
            drawInk(context, label, x + CONTENTS_ICON_WIDTH, y + 3, COLOR_INK);
            drawInk(context, mark, x + TEXT_WIDTH - markWidth, y + 3, row.page() == -1 ? COLOR_COVER : COLOR_INK_LIGHT);
        }

        if (contentsStepCount() > 1) {
            drawInk(context, tr("scroll_hint"), left + MARGIN, top + PANEL_HEIGHT - 14, COLOR_INK_LIGHT);
        }
    }

    /** MOBの目印として、スポーンエッグを小さく描く。卵のないMOBは名札。 */
    private void drawMobIcon(DrawContext context, Identifier id, int x, int y) {
        ItemStack icon = iconCache.computeIfAbsent(id, key -> {
            SpawnEggItem egg = Registries.ENTITY_TYPE.getOptionalValue(key).map(SpawnEggItem::forEntity).orElse(null);
            return new ItemStack(egg != null ? egg : Items.NAME_TAG);
        });
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0.0F);
        matrices.scale(CONTENTS_ICON_SCALE, CONTENTS_ICON_SCALE, 1.0F);
        context.drawItem(icon, 0, 0);
        matrices.pop();
    }

    /** 目次を1列ずつ送ったときの、止まる位置の数。 */
    private int contentsStepCount() {
        return stepCount(contentsRows.size(), CONTENTS_PER_PAGE);
    }

    private int nameWidth() {
        return canEdit ? TEXT_WIDTH - REMOVE_MARK_WIDTH - 2 : TEXT_WIDTH;
    }

    private void renderMobPage(DrawContext context, int mouseX, int mouseY) {
        OpenGuidePayload.Entry entry = currentEntry();
        Optional<EntityType<?>> type = Registries.ENTITY_TYPE.getOptionalValue(entry.entityId());
        int leftCenter = left + PAGE_WIDTH / 2;

        // 左ページ: MOBの名前と姿
        Text mobName = type.map(EntityType::getName).orElse(Text.literal(entry.entityId().toString()));
        drawInk(context, mobName, leftCenter - textRenderer.getWidth(mobName) / 2, top + 11, COLOR_INK);

        LivingEntity entity = getEntity(entry.entityId(), type);
        if (entity != null) {
            drawMob(context, left + MARGIN, top + 26, left + PAGE_WIDTH - MARGIN, top + PANEL_HEIGHT - 26,
                90.0F, 70.0F, 70, mouseX, mouseY, entity);
        }

        String idText = shortId(entry.entityId());
        idText = textRenderer.trimToWidth(idText, TEXT_WIDTH);
        drawInk(context, idText, leftCenter - textRenderer.getWidth(idText) / 2, top + PANEL_HEIGHT - 18, COLOR_INK_LIGHT);

        // 右ページ: 名前の一覧
        int textX = left + PAGE_WIDTH + MARGIN;
        List<String> names = entry.names();
        drawInk(context, tr("mob.names", names.size()), textX, top + 11, COLOR_INK);

        if (names.isEmpty() && canEdit) {
            drawInk(context, tr("mob.empty1"), textX, top + NAMES_TOP, COLOR_INK_LIGHT);
            drawInk(context, tr("mob.empty2"), textX, top + NAMES_TOP + LINE_HEIGHT, COLOR_INK_LIGHT);
        }

        int end = Math.min(names.size(), scroll + visibleNames());
        for (int i = scroll; i < end; i++) {
            int y = top + NAMES_TOP + (i - scroll) * LINE_HEIGHT;
            drawInk(context, textRenderer.trimToWidth("・" + names.get(i), nameWidth()), textX, y, COLOR_INK);
            if (canEdit) {
                drawInk(context, "×", textX + TEXT_WIDTH - REMOVE_MARK_WIDTH + 2, y, COLOR_WARNING);
            }
        }

        // 一覧の下: 入力した名前が使えない理由、なければ今見えている範囲
        String problem = nameProblem();
        if (problem != null) {
            drawInk(context, textRenderer.trimToWidth(tr(problem), TEXT_WIDTH), textX, top + PANEL_HEIGHT - 14, COLOR_WARNING);
        } else if (names.size() > visibleNames()) {
            drawInk(context, tr("mob.range", scroll + 1, end, names.size()), textX, top + PANEL_HEIGHT - 14, COLOR_INK_LIGHT);
        }
        if (names.size() > visibleNames()) {
            renderNameScrollBar(context, textX - 5, names.size());
        }
    }

    private void renderNameScrollBar(DrawContext context, int x, int total) {
        int barTop = top + NAMES_TOP;
        int barHeight = visibleNames() * LINE_HEIGHT - 2;
        int thumbHeight = Math.max(8, barHeight * visibleNames() / total);
        int thumbTop = barTop + (barHeight - thumbHeight) * scroll / maxNameScroll();
        context.fill(x, barTop, x + 2, barTop + barHeight, COLOR_PAPER_SHADE);
        context.fill(x, thumbTop, x + 2, thumbTop + thumbHeight, COLOR_INK_LIGHT);
    }

    private void drawMob(DrawContext context, int boxLeft, int boxTop, int boxRight, int boxBottom,
                         float maxHeight, float maxWidth, int maxSize, int mouseX, int mouseY, LivingEntity entity) {
        // 枠に収まるように、MOBの大きさに合わせて倍率を決める
        float size = Math.min(maxHeight / Math.max(entity.getHeight(), 0.1F), maxWidth / Math.max(entity.getWidth(), 0.1F));
        InventoryScreen.drawEntity(context, boxLeft, boxTop, boxRight, boxBottom,
            (int) Math.min(size, maxSize), 0.0625F, mouseX, mouseY, entity);
    }

    private void renderUnnamedPage(DrawContext context) {
        drawInk(context, tr("unnamed.title"), left + MARGIN, top + 11, COLOR_INK);
        drawInk(context, tr("unnamed.count", unnamed.size()), left + PAGE_WIDTH + MARGIN, top + 11, COLOR_INK_LIGHT);

        // 左ページを上から下へ、続きを右ページへ
        int labelWidth = canEdit ? TEXT_WIDTH - ADD_MARK_WIDTH : TEXT_WIDTH;
        int end = Math.min(unnamed.size(), scroll + UNNAMED_PER_PAGE * 2);
        for (int i = scroll; i < end; i++) {
            Identifier id = unnamed.get(i);
            int slot = i - scroll;
            int x = left + (slot / UNNAMED_PER_PAGE) * PAGE_WIDTH + MARGIN;
            int y = top + UNNAMED_TOP + (slot % UNNAMED_PER_PAGE) * UNNAMED_ROW_HEIGHT;

            Text mobName = Registries.ENTITY_TYPE.getOptionalValue(id).map(EntityType::getName).orElse(Text.literal("?"));
            drawInk(context, textRenderer.trimToWidth(mobName.getString(), labelWidth), x, y, COLOR_INK);
            drawInk(context, textRenderer.trimToWidth(shortId(id), labelWidth - 8), x + 8, y + 9, COLOR_INK_LIGHT);
            if (canEdit) {
                // ページを追加する印
                int markX = x + TEXT_WIDTH - ADD_MARK_WIDTH + 1;
                context.fill(markX, y + 2, markX + 12, y + 14, COLOR_COVER);
                drawInk(context, "+", markX + 3, y + 4, 0xFFFFFFFF);
            }
        }

        // 一番下: MOBたちからのお願い
        drawInk(context, tr("unnamed.message"), left + MARGIN, top + PANEL_HEIGHT - 14, COLOR_INK);
        if (unnamedStepCount() > 1) {
            drawInk(context, tr("scroll_hint"), left + PAGE_WIDTH + MARGIN, top + PANEL_HEIGHT - 14, COLOR_INK_LIGHT);
        }
    }

    /** 名前のないMOBの一覧を1列ずつ送ったときの、止まる位置の数。 */
    private int unnamedStepCount() {
        return stepCount(unnamed.size(), UNNAMED_PER_PAGE);
    }

    // 見開きに2列出るので、止まる位置の数は 列数 - 1
    private static int stepCount(int size, int perPage) {
        int columns = (size + perPage - 1) / perPage;
        return Math.max(1, columns - 1);
    }

    private void renderCommandPage(DrawContext context) {
        drawInk(context, tr("commands.title"), left + MARGIN, top + 11, COLOR_INK);
        if (canUseCommands()) {
            drawInk(context, tr("commands.hint"), left + PAGE_WIDTH + MARGIN, top + 11, COLOR_INK_LIGHT);
        } else {
            drawInk(context, tr("commands.no_permission"), left + PAGE_WIDTH + MARGIN, top + 11, COLOR_WARNING);
        }

        // 各ボタンの上に、何をするコマンドかを書く
        for (int i = 0; i < COMMANDS.size(); i++) {
            int x = left + (i / COMMANDS_PER_PAGE) * PAGE_WIDTH + MARGIN;
            int y = top + 24 + (i % COMMANDS_PER_PAGE) * COMMAND_ROW_HEIGHT;
            drawInk(context, tr("command." + COMMANDS.get(i).id() + ".desc"), x, y, COLOR_INK);
        }
    }

    private void renderSettingsPage(DrawContext context) {
        // 左ページ: 説明
        int textX = left + MARGIN;
        drawInk(context, tr("settings.title"), textX, top + 11, COLOR_INK);

        int y = top + 28;
        for (int i = 1; i <= SETTINGS_HELP_LINES; i++) {
            for (OrderedText line : textRenderer.wrapLines(text("settings.help." + i), TEXT_WIDTH)) {
                context.drawText(textRenderer, line, textX, y, COLOR_INK, false);
                y += 10;
            }
            y += 2;
        }

        // 右ページ: 各設定の見出し（ボタン本体はウィジェット）
        int labelX = left + PAGE_WIDTH + MARGIN;
        drawInk(context, tr("settings.show_name"), labelX, top + 16, COLOR_INK);
        drawInk(context, tr("settings.special"), labelX, top + 56, COLOR_INK);
        drawInk(context, tr("settings.chance"), labelX, top + 96, specialNamesEnabled ? COLOR_INK : COLOR_INK_LIGHT);

        if (!canEdit) {
            drawInk(context, tr("settings.no_permission"), labelX, top + 136, COLOR_WARNING);
        }
    }

    private void drawInk(DrawContext context, String text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, false);
    }

    private void drawInk(DrawContext context, Text text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, false);
    }

    /** 表示用のMOBを作る（種類ごとに1回だけ）。生き物でない・作れない場合は null。 */
    private LivingEntity getEntity(Identifier id, Optional<EntityType<?>> type) {
        return entityCache.computeIfAbsent(id, key -> {
            if (type.isEmpty() || client == null || client.world == null) {
                return Optional.empty();
            }
            try {
                Entity entity = type.get().create(client.world, SpawnReason.LOAD);
                return entity instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
            } catch (RuntimeException e) {
                AnimalNamerMod.LOGGER.warn("[Auto Name Tag] Could not create a mob to show in the guide: {}", key, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * オンとオフを左右に並べ、今の状態の側を色と「✔」で示すスイッチ。
     * 1つのボタンの文字が変わるだけだと、今の状態なのか押した後の状態なのか分かりにくいため。
     */
    private class OnOffToggle extends ClickableWidget {

        private final String labelKey;
        private final BooleanSupplier state;
        private final Runnable toggle;

        OnOffToggle(int x, int y, int width, int height, String labelKey, BooleanSupplier state, Runnable toggle) {
            super(x, y, width, height, Text.empty());
            this.labelKey = labelKey;
            this.state = state;
            this.toggle = toggle;
            setTooltip(Tooltip.of(text(labelKey + ".tooltip")));
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int half = width / 2;
            boolean on = state.getAsBoolean();
            boolean mouseOnLeft = mouseX < getX() + half;
            drawSide(context, getX(), half, text("on"), on, COLOR_COVER, isHovered() && mouseOnLeft);
            drawSide(context, getX() + half, width - half, text("off"), !on, COLOR_TOGGLE_OFF, isHovered() && !mouseOnLeft);
            context.drawBorder(getX(), getY(), width, height, isSelected() && active ? COLOR_INK : COLOR_INK_LIGHT);
        }

        private void drawSide(DrawContext context, int x, int sideWidth, Text label, boolean current, int color, boolean hovering) {
            int center = x + sideWidth / 2;
            int textY = getY() + (height - 8) / 2;
            if (current) {
                context.fill(x, getY(), x + sideWidth, getY() + height, color);
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("✔ ").append(label), center, textY, 0xFFFFFFFF);
            } else {
                context.fill(x, getY(), x + sideWidth, getY() + height, hovering && active ? COLOR_PAPER : COLOR_PAPER_SHADE);
                context.drawText(textRenderer, label, center - textRenderer.getWidth(label) / 2, textY, COLOR_INK_LIGHT, false);
            }
        }

        // 今と反対の側を押したときだけ切り替える
        @Override
        public void onClick(double mouseX, double mouseY) {
            if ((mouseX < getX() + width / 2.0) != state.getAsBoolean()) {
                toggle.run();
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            boolean press = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE;
            if (active && visible && press) {
                playDownSound(client.getSoundManager());
                toggle.run();
                return true;
            }
            return false;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            setMessage(text(labelKey).copy().append(": ").append(onOffText(state.getAsBoolean())));
            appendDefaultNarrations(builder);
        }
    }

    private class ChanceSlider extends SliderWidget {

        ChanceSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), specialNamesChance / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(specialNamesChance + "%"));
        }

        @Override
        protected void applyValue() {
            specialNamesChance = (int) Math.round(value * 100);
            dirty = true;
        }

        // ドラッグ中は送らず、離したときにまとめて送る
        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            sendSettings();
        }
    }
}
