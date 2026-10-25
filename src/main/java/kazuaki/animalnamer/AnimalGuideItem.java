package kazuaki.animalnamer;

import kazuaki.animalnamer.network.GuideNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * どうぶつ名鑑。右クリックで、MOBごとの名前一覧と設定のページを開く。
 */
public class AnimalGuideItem extends Item {

    public AnimalGuideItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // 名前と設定はサーバーが持っているので、サーバーから中身を送って開かせる
        if (user instanceof ServerPlayerEntity player) {
            GuideNetworking.openGuide(player);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }
}
