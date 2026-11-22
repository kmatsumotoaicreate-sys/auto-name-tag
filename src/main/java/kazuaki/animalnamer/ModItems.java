package kazuaki.animalnamer;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ModItems {

    // 配布済みの目印。コマンドタグはプレイヤーデータに保存され、リスポーン後も引き継がれる
    private static final String GUIDE_GIVEN_TAG = "animalnamer_guide_given";

    private static final RegistryKey<Item> ANIMAL_GUIDE_KEY =
        RegistryKey.of(RegistryKeys.ITEM, Identifier.of(AnimalNamerMod.MOD_ID, "animal_guide"));

    public static final Item ANIMAL_GUIDE = Registry.register(
        Registries.ITEM,
        ANIMAL_GUIDE_KEY,
        new AnimalGuideItem(new Item.Settings().registryKey(ANIMAL_GUIDE_KEY).maxCount(1)));

    private ModItems() {
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ANIMAL_GUIDE));

        // 初めてワールドに入ったプレイヤーに1冊渡す
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveGuideOnce(handler.getPlayer()));
    }

    private static void giveGuideOnce(ServerPlayerEntity player) {
        if (player.getCommandTags().contains(GUIDE_GIVEN_TAG)) {
            return;
        }
        ItemStack stack = new ItemStack(ANIMAL_GUIDE);
        if (!player.giveItemStack(stack)) {
            player.dropItem(stack, false);
        }
        player.addCommandTag(GUIDE_GIVEN_TAG);
    }
}
