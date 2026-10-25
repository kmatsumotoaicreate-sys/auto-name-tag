package kazuaki.animalnamer.mixin;

import kazuaki.animalnamer.AnimalNamerMod;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimalEntity.class)
public class AnimalBreedMixin {

    /**
     * 繁殖で子供が生まれた瞬間に名前を付ける。
     * breed(ServerWorld, AnimalEntity, PassiveEntity) の第3引数が子供エンティティ。
     */
    @Inject(
        method = "breed(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;Lnet/minecraft/entity/passive/PassiveEntity;)V",
        at = @At("HEAD")
    )
    private void animalnamer$onBreed(ServerWorld world, AnimalEntity other, PassiveEntity baby, CallbackInfo ci) {
        if (baby != null) {
            AnimalNamerMod.applyName(baby);
        }
    }
}
