package kazuaki.animalnamer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.RegistryEntryReferenceArgumentType;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * /animalnamer コマンド（OP権限）。
 *
 *   reload                  設定ファイルを読み直す
 *   add <MOB> <名前>        名前を追加する
 *   remove <MOB> <名前>     名前を削除する
 *   list [MOB]              設定されている名前を表示する
 *   nameall                 読み込み済みの名前なしMOBにまとめて名前を付ける
 *   showname on|off         名前の常時表示を切り替える
 *   special on|off          特別な効果のある名前を全MOBに付けるか切り替える
 *   special chance <0-100>  特別な名前が付く確率を変える
 */
public final class AnimalNamerCommands {

    private static final String PREFIX = "[Auto Name Tag] ";
    private static final String KEY = "commands." + AnimalNamerMod.MOD_ID + ".";
    private static final String ARG_ENTITY = "entity";
    private static final String ARG_NAME = "name";
    private static final String ARG_CHANCE = "percent";

    private AnimalNamerCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal(AnimalNamerMod.MOD_ID)
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("reload")
                    .executes(context -> reload(context.getSource())))
                .then(CommandManager.literal("add")
                    .executes(context -> info(context.getSource(), message("usage.add")))
                    .then(entityArgument(registryAccess)
                        .then(CommandManager.argument(ARG_NAME, StringArgumentType.greedyString())
                            .executes(AnimalNamerCommands::add))))
                .then(CommandManager.literal("remove")
                    .executes(context -> info(context.getSource(), message("usage.remove")))
                    .then(entityArgument(registryAccess)
                        .then(CommandManager.argument(ARG_NAME, StringArgumentType.greedyString())
                            .executes(AnimalNamerCommands::remove))))
                .then(CommandManager.literal("list")
                    .executes(context -> listAll(context.getSource()))
                    .then(entityArgument(registryAccess)
                        .executes(AnimalNamerCommands::listOne)))
                .then(CommandManager.literal("nameall")
                    .executes(context -> nameAll(context.getSource())))
                .then(onOff("showname", AnimalNamerCommands::setShowName,
                    () -> message("showname.status", onOffText(ModConfig.isShowNameAlways()))))
                .then(onOff("special", AnimalNamerCommands::setSpecial,
                    () -> message("special.status", onOffText(ModConfig.isSpecialNamesEnabled()),
                        ModConfig.getSpecialNamesChance()))
                    .then(CommandManager.literal("chance")
                        .executes(context -> info(context.getSource(),
                            message("chance.status", ModConfig.getSpecialNamesChance())))
                        .then(CommandManager.argument(ARG_CHANCE, IntegerArgumentType.integer(0, 100))
                            .executes(AnimalNamerCommands::setSpecialChance))))));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, ?> entityArgument(
            CommandRegistryAccess registryAccess) {
        return CommandManager.argument(ARG_ENTITY,
                RegistryEntryReferenceArgumentType.registryEntry(registryAccess, RegistryKeys.ENTITY_TYPE))
            .suggests(SuggestionProviders.SUMMONABLE_ENTITIES);
    }

    private interface Toggle {
        int apply(ServerCommandSource source, boolean enabled);
    }

    // on / off を付けずに実行したときは、エラーにせず現在の状態と使い方を表示する
    private static LiteralArgumentBuilder<ServerCommandSource> onOff(String name, Toggle toggle, Supplier<Text> status) {
        return CommandManager.literal(name)
            .executes(context -> info(context.getSource(), status.get()))
            .then(CommandManager.literal("on").executes(context -> toggle.apply(context.getSource(), true)))
            .then(CommandManager.literal("off").executes(context -> toggle.apply(context.getSource(), false)));
    }

    private static EntityType<?> getEntityType(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return RegistryEntryReferenceArgumentType.getSummonableEntityType(context, ARG_ENTITY).value();
    }

    private static int reload(ServerCommandSource source) {
        boolean configLoaded = ModConfig.reload();
        boolean namesLoaded = NamesConfig.reload();

        if (!configLoaded || !namesLoaded) {
            source.sendError(message("reload.failed"));
            return 0;
        }

        int count = NamesConfig.getAllNames().size();
        feedback(source, message("reload.success", count));
        return count;
    }

    private static int add(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        EntityType<?> type = getEntityType(context);
        String name = StringArgumentType.getString(context, ARG_NAME).trim();

        if (!NamesConfig.isValidName(name)) {
            source.sendError(message("add.invalid"));
            return 0;
        }

        try {
            if (!NamesConfig.addName(type, name)) {
                source.sendError(message("add.exists", name));
                return 0;
            }
        } catch (IOException e) {
            return writeFailed(source, e);
        }

        feedback(source, message("add.success", type.getName(), name));
        return 1;
    }

    private static int remove(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        EntityType<?> type = getEntityType(context);
        String name = StringArgumentType.getString(context, ARG_NAME).trim();

        try {
            if (!NamesConfig.removeName(type, name)) {
                source.sendError(message("remove.missing", name));
                return 0;
            }
        } catch (IOException e) {
            return writeFailed(source, e);
        }

        feedback(source, message("remove.success", type.getName(), name));
        return 1;
    }

    private static int listAll(ServerCommandSource source) {
        Map<String, List<String>> all = new TreeMap<>(NamesConfig.getAllNames());
        if (all.isEmpty()) {
            return info(source, message("list.empty")) - 1;
        }

        info(source, message("list.header", all.size()));
        all.forEach((key, names) ->
            source.sendFeedback(() -> Text.literal("  " + key + ": " + String.join(", ", names)), false));
        return all.size();
    }

    private static int listOne(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        EntityType<?> type = getEntityType(context);
        List<String> names = NamesConfig.getNames(type);

        if (names.isEmpty()) {
            return info(source, message("list.none", type.getName())) - 1;
        }

        info(source, message("list.one", type.getName(), String.join(", ", names)));
        return names.size();
    }

    private static int nameAll(ServerCommandSource source) {
        int count = 0;
        for (ServerWorld world : source.getServer().getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof MobEntity mob && AnimalNamerMod.applyName(mob)) {
                    count++;
                }
            }
        }

        feedback(source, message("nameall.success", count));
        return count;
    }

    private static int setShowName(ServerCommandSource source, boolean enabled) {
        ModConfig.setShowNameAlways(enabled);
        return saveConfig(source, message("showname.set", onOffText(enabled)));
    }

    private static int setSpecial(ServerCommandSource source, boolean enabled) {
        ModConfig.setSpecialNamesEnabled(enabled);
        return saveConfig(source, message("special.set", onOffText(enabled), ModConfig.getSpecialNamesChance()));
    }

    private static int setSpecialChance(CommandContext<ServerCommandSource> context) {
        ModConfig.setSpecialNamesChance(IntegerArgumentType.getInteger(context, ARG_CHANCE));
        return saveConfig(context.getSource(), message("chance.set", ModConfig.getSpecialNamesChance(),
            onOffText(ModConfig.isSpecialNamesEnabled())));
    }

    private static int saveConfig(ServerCommandSource source, Text message) {
        try {
            ModConfig.save();
        } catch (IOException e) {
            return writeFailed(source, e);
        }
        feedback(source, message);
        return 1;
    }

    private static int writeFailed(ServerCommandSource source, IOException e) {
        AnimalNamerMod.LOGGER.error("[Auto Name Tag] Failed to write the config file", e);
        source.sendError(message("write_failed"));
        return 0;
    }

    /** 言語ファイルの commands.animalnamer.<key> を、先頭にMOD名を付けて返す（見る人の言語で表示される）。 */
    private static MutableText message(String key, Object... args) {
        return Text.literal(PREFIX).append(Text.translatable(KEY + key, args));
    }

    private static Text onOffText(boolean enabled) {
        return Text.translatable(KEY + (enabled ? "on" : "off"));
    }

    // 実行した本人にだけ見せる（状態の表示や使い方）
    private static int info(ServerCommandSource source, Text message) {
        source.sendFeedback(() -> message, false);
        return 1;
    }

    // 変更の結果。ほかのOPにも知らせる
    private static void feedback(ServerCommandSource source, Text message) {
        source.sendFeedback(() -> message, true);
    }
}
