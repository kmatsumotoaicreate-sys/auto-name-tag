package kazuaki.animalnamer.network;

import kazuaki.animalnamer.AnimalNamerMod;
import kazuaki.animalnamer.ModConfig;
import kazuaki.animalnamer.NamesConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * どうぶつ名鑑の通信（サーバー側）。
 */
public final class GuideNetworking {

    private GuideNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(OpenGuidePayload.ID, OpenGuidePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateSettingsPayload.ID, UpdateSettingsPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(EditNamesPayload.ID, EditNamesPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(UpdateSettingsPayload.ID, (payload, context) ->
            context.server().execute(() -> applySettings(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(EditNamesPayload.ID, (payload, context) ->
            context.server().execute(() -> applyEdit(context.player(), payload)));
    }

    /** プレイヤーの画面にどうぶつ名鑑を開かせる。 */
    public static void openGuide(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new OpenGuidePayload(
            buildEntries(),
            buildUnnamed(),
            ModConfig.isShowNameAlways(),
            ModConfig.isSpecialNamesEnabled(),
            ModConfig.getSpecialNamesChance(),
            canEdit(player)
        ));
    }

    /** 設定を変えられるのは、OP権限を持つ人とワールドの持ち主（シングルプレイのホスト）。 */
    private static boolean canEdit(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) || player.server.isHost(player.getGameProfile());
    }

    private static void applySettings(ServerPlayerEntity player, UpdateSettingsPayload payload) {
        if (!canEdit(player)) {
            return;
        }
        ModConfig.setShowNameAlways(payload.showNameAlways());
        ModConfig.setSpecialNamesEnabled(payload.specialNamesEnabled());
        ModConfig.setSpecialNamesChance(payload.specialNamesChance());
        try {
            ModConfig.save();
        } catch (IOException e) {
            AnimalNamerMod.LOGGER.error("[Auto Name Tag] Failed to write the config file", e);
        }
    }

    private static void applyEdit(ServerPlayerEntity player, EditNamesPayload payload) {
        if (!canEdit(player)) {
            return;
        }
        Optional<EntityType<?>> type = Registries.ENTITY_TYPE.getOptionalValue(payload.entityId());
        if (type.isEmpty()) {
            return;
        }

        String name = payload.name().trim();
        try {
            switch (payload.action()) {
                case ADD -> {
                    if (NamesConfig.isValidName(name)) {
                        NamesConfig.addName(type.get(), name);
                    }
                }
                case REMOVE -> NamesConfig.removeName(type.get(), name);
                case CLEAR -> NamesConfig.removeAllNames(type.get());
            }
        } catch (IOException e) {
            AnimalNamerMod.LOGGER.error("[Auto Name Tag] Failed to write names.txt", e);
            player.sendMessage(Text.literal("[Auto Name Tag] ").append(Text.translatable("commands.animalnamer.save_failed")), false);
        }

        // 開いている名鑑を最新の内容に更新させる
        openGuide(player);
    }

    /** names.txt に [ID] はあるが名前が書かれていないMOBを、書かれた順に並べる。 */
    private static List<Identifier> buildUnnamed() {
        List<String> keys = NamesConfig.getUnnamedKeys();

        record Indexed(int order, Identifier id) {
        }
        List<Indexed> found = new ArrayList<>();

        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            if (!NamesConfig.getNames(type).isEmpty()) {
                continue;
            }
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            int order = keys.indexOf(id.toString());
            if (order == -1) {
                order = keys.indexOf(id.getPath());
            }
            if (order != -1) {
                found.add(new Indexed(order, id));
            }
        }

        found.sort(Comparator.comparingInt(Indexed::order));
        return found.stream().map(Indexed::id).toList();
    }

    /** 名前が設定されているMOBを、names.txt に書かれた順に並べる。 */
    private static List<OpenGuidePayload.Entry> buildEntries() {
        List<String> keys = new ArrayList<>(NamesConfig.getAllNames().keySet());

        record Indexed(int order, OpenGuidePayload.Entry entry) {
        }
        List<Indexed> found = new ArrayList<>();

        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            List<String> names = NamesConfig.getNames(type);
            if (names.isEmpty()) {
                continue;
            }
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            int order = keys.indexOf(id.toString());
            if (order == -1) {
                order = keys.indexOf(id.getPath());
            }
            found.add(new Indexed(order, new OpenGuidePayload.Entry(id, names)));
        }

        found.sort(Comparator.comparingInt(Indexed::order));
        return found.stream().map(Indexed::entry).toList();
    }
}
