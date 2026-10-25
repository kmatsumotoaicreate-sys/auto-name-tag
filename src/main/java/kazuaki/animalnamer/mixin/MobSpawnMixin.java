package kazuaki.animalnamer.mixin;

import kazuaki.animalnamer.AnimalNamerMod;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobSpawnMixin {

    @Inject(method = "initialize", at = @At("RETURN"))
    private void animalnamer$onInitialize(
            ServerWorldAccess world,
            LocalDifficulty difficulty,
            SpawnReason spawnReason,
            EntityData entityData,
            CallbackInfoReturnable<EntityData> cir
    ) {
        // 自然スポーン・チャンク生成・スポーンエッグのみ対象
        // ※繁殖は AnimalBreedMixin で別途処理
        if (spawnReason != SpawnReason.NATURAL
                && spawnReason != SpawnReason.CHUNK_GENERATION
                && spawnReason != SpawnReason.SPAWN_EGG) {
            return;
        }

        AnimalNamerMod.applyName((MobEntity) (Object) this);
    }
}
