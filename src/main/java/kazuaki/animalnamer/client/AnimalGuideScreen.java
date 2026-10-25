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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * どうぶつ名鑑の画面。
 * 見開きの左ページにMOBの姿、右ページにそのMOBに付く名前を表示する。
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

    // まだ名前のないMOBの一覧: 1件を2行（MOB名・ID）で、左右のページに7件ずつ
    private static final int UNNAMED_PER_PAGE = 7;
    private static final int UNNAMED_ROW_HEIGHT = 19;
    private static final int UNNAMED_TOP = 26;
    private static final int ADD_MARK_WIDTH = 14;

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

    private int page;
    private int scroll;
    private String pendingName = "";
    private boolean confirmDelete;
    private final Map<Identifier, Optional<LivingEntity>> entityCache = new HashMap<>();

    private int left;
    private int top;
    private ButtonWidget showNameButton;
    private ButtonWidget specialButton;
    private ChanceSlider chanceSlider;
    private final List<ButtonWidget> commandButtons = new ArrayList<>();
    private TextFieldWidget nameField;
    private ButtonWidget addNameButton;
    private ButtonWidget deletePageButton;

    public AnimalGuideScreen(OpenGuidePayload payload) {
        super(Text.translatable("item.animalnamer.animal_guide"));
        applyPayload(payload);
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
    }

    private void rebuildMobPages() {
        List<OpenGuidePayload.Entry> pages = new ArrayList<>(entries);
        if (draft != null) {
            pages.add(new OpenGuidePayload.Entry(draft, List.of()));
        }
        mobPages = pages;
    }

    /** 名前の追加・削除のあと、サーバーから届いた最新の内容に差し替える。見ていたページはできるだけ保つ。 */
    public void update(OpenGuidePayload payload) {
        Identifier viewing = isMobPage() ? mobPages.get(page).entityId() : null;
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
                page = index;
                scroll = MathHelper.clamp(scroll, 0, maxNameScroll());
            } else if (viewing.equals(draft)) {
                page = mobPages.size() - 1;
            } else {
                // ページごと削除された: 名前のないMOBの一覧へ
                page = Math.min(mobPages.size(), pageCount() - 1);
                scroll = 0;
            }
        } else if (onUnnamed) {
            page = unnamed.isEmpty() ? commandPageIndex() : mobPages.size();
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
        addDrawableChild(ButtonWidget.builder(text("next"), button -> turnPage(1))
            .dimensions(left + PANEL_WIDTH - 60, buttonY, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(text("close"), button -> close())
            .dimensions(left + PANEL_WIDTH / 2 - 30, buttonY, 60, 20).build());

        int rightX = left + PAGE_WIDTH + MARGIN;
        showNameButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            showNameAlways = !showNameAlways;
            settingsChanged();
        }).dimensions(rightX, top + 27, TEXT_WIDTH, 20).build());
        specialButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            specialNamesEnabled = !specialNamesEnabled;
            settingsChanged();
        }).dimensions(rightX, top + 67, TEXT_WIDTH, 20).build());
        chanceSlider = addDrawableChild(new ChanceSlider(rightX, top + 107, TEXT_WIDTH, 20));

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

        // MOBのページの編集欄: 名前の入力・追加と、ページの削除
        int editY = top + NAMES_TOP + VISIBLE_NAMES_EDITING * LINE_HEIGHT + 4;
        nameField = addDrawableChild(new TextFieldWidget(textRenderer, rightX, editY, TEXT_WIDTH - 40, 18,
            text("name_field")));
        nameField.setMaxLength(NamesConfig.MAX_NAME_LENGTH);
        nameField.setPlaceholder(text("name_placeholder"));
        nameField.setText(pendingName);
        nameField.setChangedListener(text -> pendingName = text);
        addNameButton = addDrawableChild(ButtonWidget.builder(text("add"), button -> submitName())
            .dimensions(rightX + TEXT_WIDTH - 36, editY - 1, 36, 20).build());
        deletePageButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> deletePage())
            .dimensions(rightX + TEXT_WIDTH - 64, top + 7, 64, 14).build());

        updateWidgets();
    }

    // ページの並び: MOBのページ → まだ名前のないMOB（あれば）→ コマンド一覧 → 設定
    private int commandPageIndex() {
        return mobPages.size() + (unnamed.isEmpty() ? 0 : 1);
    }

    private int pageCount() {
        return commandPageIndex() + 2;
    }

    private boolean isMobPage() {
        return page < mobPages.size();
    }

    private boolean isDraftPage() {
        return draft != null && page == mobPages.size() - 1;
    }

    private boolean isUnnamedPage() {
        return !unnamed.isEmpty() && page == mobPages.size();
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
        return isMobPage() ? Math.max(0, mobPages.get(page).names().size() - visibleNames()) : 0;
    }

    private void turnPage(int delta) {
        int target = page + delta;
        // 名前を付けないまま仮のページを離れたら、そのページは片付ける
        if (isDraftPage()) {
            draft = null;
            rebuildMobPages();
            if (delta > 0) {
                target--;
            }
        }
        // 最初と最後のページをつなげる
        page = Math.floorMod(target, pageCount());
        scroll = 0;
        confirmDelete = false;
        pendingName = "";
        nameField.setText("");
        updateWidgets();
    }

    private void updateWidgets() {
        boolean settings = isSettingsPage();
        boolean commands = isCommandPage();
        boolean commandsUsable = canUseCommands();
        for (ButtonWidget button : commandButtons) {
            button.visible = commands;
            button.active = commandsUsable;
        }

        showNameButton.visible = settings;
        specialButton.visible = settings;
        chanceSlider.visible = settings;
        showNameButton.active = canEdit;
        specialButton.active = canEdit;
        chanceSlider.active = canEdit;
        showNameButton.setMessage(onOffText(showNameAlways));
        specialButton.setMessage(onOffText(specialNamesEnabled));

        boolean editing = isMobPage() && canEdit;
        nameField.visible = editing;
        addNameButton.visible = editing;
        deletePageButton.visible = editing && !mobPages.get(page).names().isEmpty();
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
            ClientPlayNetworking.send(new EditNamesPayload(action, mobPages.get(page).entityId(), name));
        }
    }

    private void submitName() {
        String name = pendingName.trim();
        if (!isMobPage() || !NamesConfig.isValidName(name)) {
            return;
        }
        sendEdit(EditNamesPayload.Action.ADD, name);
        pendingName = "";
        nameField.setText("");
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

    /** 名前のないMOBの一覧から、そのMOBの仮のページを作って開く。 */
    private void openDraft(Identifier id) {
        draft = id;
        rebuildMobPages();
        page = mobPages.size() - 1;
        scroll = 0;
        pendingName = "";
        confirmDelete = false;
        clearAndInit();
        setFocused(nameField);
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
        if (nameField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            submitName();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        if (isMobPage()) {
            int index = removeMarkAt(mouseX, mouseY);
            if (index != -1) {
                sendEdit(EditNamesPayload.Action.REMOVE, mobPages.get(page).names().get(index));
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
        if (isUnnamedPage()) {
            // 1列（7件）ずつ送る
            int step = MathHelper.clamp(scroll / UNNAMED_PER_PAGE - (int) Math.signum(verticalAmount),
                0, unnamedStepCount() - 1);
            scroll = step * UNNAMED_PER_PAGE;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** マウスが名前の右の「×」に乗っていれば、その名前の番号。乗っていなければ -1。 */
    private int removeMarkAt(double mouseX, double mouseY) {
        if (!canEdit || !isMobPage()) {
            return -1;
        }
        int right = left + PAGE_WIDTH + MARGIN + TEXT_WIDTH;
        if (mouseX < right - REMOVE_MARK_WIDTH || mouseX >= right || mouseY < top + NAMES_TOP) {
            return -1;
        }
        int index = scroll + (int) (mouseY - top - NAMES_TOP) / LINE_HEIGHT;
        int end = Math.min(mobPages.get(page).names().size(), scroll + visibleNames());
        return index < end ? index : -1;
    }

    /** マウスが名前のないMOBの一覧の行に乗っていれば、そのMOBの番号。乗っていなければ -1。 */
    private int unnamedIndexAt(double mouseX, double mouseY) {
        double relativeX = mouseX - left;
        double relativeY = mouseY - top - UNNAMED_TOP;
        if (relativeX < 0 || relativeX >= PANEL_WIDTH || relativeY < 0) {
            return -1;
        }
        int column = (int) relativeX / PAGE_WIDTH;
        double inPage = relativeX - column * PAGE_WIDTH;
        int row = (int) relativeY / UNNAMED_ROW_HEIGHT;
        if (inPage < MARGIN || inPage >= MARGIN + TEXT_WIDTH || row >= UNNAMED_PER_PAGE) {
            return -1;
        }
        int index = scroll + column * UNNAMED_PER_PAGE + row;
        return index < unnamed.size() ? index : -1;
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
        context.fill(left - 5, top - 5, right + 5, bottom + 5, COLOR_COVER_EDGE);
        context.fill(left - 4, top - 4, right + 4, bottom + 4, COLOR_COVER);
        context.fill(left, top, right, bottom, COLOR_PAPER);
        // 見開きの中央の折り目
        context.fill(left + PAGE_WIDTH - 1, top, left + PAGE_WIDTH + 1, bottom, COLOR_PAPER_SHADE);

        if (isMobPage()) {
            context.fill(left + MARGIN, top + 26, left + PAGE_WIDTH - MARGIN, bottom - 26, COLOR_PAPER_SHADE);
        } else if (isUnnamedPage() && (canEdit || canUseCommands())) {
            // マウスを乗せた行を目立たせる
            int index = unnamedIndexAt(mouseX, mouseY);
            if (index != -1) {
                int slot = index - scroll;
                int x = left + (slot / UNNAMED_PER_PAGE) * PAGE_WIDTH + MARGIN;
                int y = top + UNNAMED_TOP + (slot % UNNAMED_PER_PAGE) * UNNAMED_ROW_HEIGHT;
                context.fill(x - 2, y - 1, x + TEXT_WIDTH + 2, y + UNNAMED_ROW_HEIGHT - 1, COLOR_PAPER_SHADE);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (isSettingsPage()) {
            renderSettingsPage(context);
        } else if (isCommandPage()) {
            renderCommandPage(context);
        } else if (isUnnamedPage()) {
            renderUnnamedPage(context);
        } else {
            renderMobPage(context, mouseX, mouseY);
        }

        // 右下: 本全体のページ。名前のないMOBの一覧では、一覧の中のどこを見ているかを出す
        String pageText = isUnnamedPage()
            ? (scroll / UNNAMED_PER_PAGE + 1) + " / " + unnamedStepCount()
            : (page + 1) + " / " + pageCount();
        drawInk(context, pageText, left + PANEL_WIDTH - MARGIN - textRenderer.getWidth(pageText),
            top + PANEL_HEIGHT - 14, COLOR_INK_LIGHT);

        renderTooltip(context, mouseX, mouseY);
    }

    private void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        if (removeMarkAt(mouseX, mouseY) != -1) {
            context.drawTooltip(textRenderer, text("tooltip.remove_name"), mouseX, mouseY);
        } else if (isUnnamedPage() && unnamedIndexAt(mouseX, mouseY) != -1) {
            if (canEdit && isOnAddMark(mouseX)) {
                context.drawTooltip(textRenderer, text("tooltip.add_page"), mouseX, mouseY);
            } else if (canUseCommands()) {
                context.drawTooltip(textRenderer, text("tooltip.add_command"), mouseX, mouseY);
            }
        }
    }

    private void renderMobPage(DrawContext context, int mouseX, int mouseY) {
        OpenGuidePayload.Entry entry = mobPages.get(page);
        Optional<EntityType<?>> type = Registries.ENTITY_TYPE.getOrEmpty(entry.entityId());
        int leftCenter = left + PAGE_WIDTH / 2;

        // 左ページ: MOBの名前と姿
        Text mobName = type.map(EntityType::getName).orElse(Text.literal(entry.entityId().toString()));
        drawInk(context, mobName, leftCenter - textRenderer.getWidth(mobName) / 2, top + 11, COLOR_INK);

        LivingEntity entity = getEntity(entry.entityId(), type);
        if (entity != null) {
            int boxLeft = left + MARGIN;
            int boxTop = top + 26;
            int boxRight = left + PAGE_WIDTH - MARGIN;
            int boxBottom = top + PANEL_HEIGHT - 26;
            // 枠に収まるように、MOBの大きさに合わせて倍率を決める
            float size = Math.min(90.0F / Math.max(entity.getHeight(), 0.1F), 70.0F / Math.max(entity.getWidth(), 0.1F));
            InventoryScreen.drawEntity(context, boxLeft, boxTop, boxRight, boxBottom,
                (int) Math.min(size, 70.0F), 0.0625F, mouseX, mouseY, entity);
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

        int nameWidth = canEdit ? TEXT_WIDTH - REMOVE_MARK_WIDTH - 2 : TEXT_WIDTH;
        int end = Math.min(names.size(), scroll + visibleNames());
        for (int i = scroll; i < end; i++) {
            int y = top + NAMES_TOP + (i - scroll) * LINE_HEIGHT;
            drawInk(context, textRenderer.trimToWidth("・" + names.get(i), nameWidth), textX, y, COLOR_INK);
            if (canEdit) {
                drawInk(context, "×", textX + TEXT_WIDTH - REMOVE_MARK_WIDTH + 2, y, COLOR_WARNING);
            }
        }

        if (names.size() > visibleNames()) {
            drawInk(context, tr("scroll_hint"), textX, top + PANEL_HEIGHT - 14, COLOR_INK_LIGHT);
        }
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

            Text mobName = Registries.ENTITY_TYPE.getOrEmpty(id).map(EntityType::getName).orElse(Text.literal("?"));
            drawInk(context, textRenderer.trimToWidth(mobName.getString(), labelWidth), x, y, COLOR_INK);
            drawInk(context, textRenderer.trimToWidth(shortId(id), labelWidth - 8), x + 8, y + 9, COLOR_INK_LIGHT);
            if (canEdit) {
                // ページを追加する印
                drawInk(context, "+", x + TEXT_WIDTH - ADD_MARK_WIDTH + 4, y + 4, COLOR_COVER);
            }
        }

        // 一番下: MOBたちからのお願い
        drawInk(context, tr("unnamed.message"), left + MARGIN, top + PANEL_HEIGHT - 14, COLOR_INK);
        if (unnamedStepCount() > 1) {
            drawInk(context, tr("scroll_hint"), left + PAGE_WIDTH + MARGIN, top + PANEL_HEIGHT - 14, COLOR_INK_LIGHT);
        }
    }

    /** 名前のないMOBの一覧を1列ずつ送ったときの、止まる位置の数（見開きに2列出るので 列数 - 1）。 */
    private int unnamedStepCount() {
        int columns = (unnamed.size() + UNNAMED_PER_PAGE - 1) / UNNAMED_PER_PAGE;
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
        drawInk(context, tr("settings.chance"), labelX, top + 96, COLOR_INK);

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
                Entity entity = type.get().create(client.world);
                return entity instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
            } catch (RuntimeException e) {
                AnimalNamerMod.LOGGER.warn("[Auto Name Tag] Could not create a mob to show in the guide: {}", key, e);
                return Optional.empty();
            }
        }).orElse(null);
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
