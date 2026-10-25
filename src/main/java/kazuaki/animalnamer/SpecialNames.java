package kazuaki.animalnamer;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * バニラで特別な効果がある名前（イースターエッグ）を選ぶクラス。
 */
public final class SpecialNames {

    private SpecialNames() {
    }

    /**
     * 設定で有効になっていて確率に当たった場合に、そのMOBで効果が出る名前を返す。
     * 外れた場合や無効の場合は null。
     */
    public static String roll(MobEntity mob) {
        if (!ModConfig.isSpecialNamesEnabled()) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(100) >= ModConfig.getSpecialNamesChance()) {
            return null;
        }

        // 上下逆さまはどのMOBでも効果がある
        List<String> candidates = new ArrayList<>(List.of("Dinnerbone", "Grumm"));
        if (mob instanceof SheepEntity) {
            candidates.add("jeb_");
        } else if (mob instanceof RabbitEntity) {
            candidates.add("Toast");
        } else if (mob instanceof VindicatorEntity) {
            candidates.add("Johnny");
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
}
