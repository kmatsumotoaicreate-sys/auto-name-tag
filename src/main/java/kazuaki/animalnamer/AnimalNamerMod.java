package kazuaki.animalnamer;

import kazuaki.animalnamer.network.GuideNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

public class AnimalNamerMod implements ModInitializer {

    public static final String MOD_ID = "animalnamer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * 名前を付けるスポーン理由。ワールドに出てくるMOBが対象（スポナー・バケツ・変身などは対象外）。
     * STRUCTURE: 村と一緒に生成されるネコ・ウマなど / MOB_SUMMONED: 村人が呼ぶアイアンゴーレム /
     * EVENT: 行商人・襲撃 / PATROL: 略奪隊 / DISPENSER: ディスペンサーのスポーンエッグ
     */
    public static final Set<SpawnReason> NAMED_SPAWN_REASONS = EnumSet.of(
        SpawnReason.NATURAL, SpawnReason.CHUNK_GENERATION, SpawnReason.STRUCTURE,
        SpawnReason.SPAWN_EGG, SpawnReason.DISPENSER, SpawnReason.MOB_SUMMONED,
        SpawnReason.EVENT, SpawnReason.PATROL, SpawnReason.COMMAND
    );

    @Override
    public void onInitialize() {
        ModConfig.load();
        NamesConfig.load();
        ModItems.initialize();
        GuideNetworking.initialize();
        AnimalNamerCommands.register();

        // 他のMODのMOBは、全MODの登録が終わったサーバー起動時に names.txt へ追記する
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                NamesConfig.addMissingModSections();
            } catch (IOException e) {
                LOGGER.error("[Auto Name Tag] Failed to add mobs from other mods to names.txt", e);
            }
        });
        LOGGER.info("[Auto Name Tag] Mod loaded");
        LOGGER.info("[Auto Name Tag] Name list file: {}", NamesConfig.getConfigPath());
    }

    /**
     * まだ名前のないMOBに名前を付ける。
     * 特別な名前が有効で確率に当たればそれを、そうでなければ names.txt からランダムに選ぶ。
     *
     * @return 名前を付けたら true
     */
    public static boolean applyName(MobEntity mob) {
        if (mob.hasCustomName()) {
            return false;
        }
        String name = SpecialNames.roll(mob);
        if (name == null) {
            name = NamesConfig.getRandomName(mob.getType());
        }
        if (name == null) {
            return false;
        }
        mob.setCustomName(Text.literal(name));
        if (ModConfig.isShowNameAlways()) {
            mob.setCustomNameVisible(true);
        }
        return true;
    }
}
