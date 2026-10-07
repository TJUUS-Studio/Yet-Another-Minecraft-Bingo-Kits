package me.chengzhify.mixin;

import me.chengzhify.kit.KitItemOption;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void yabbingo$blockPickupForOtherPlayers(Player player, CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (!KitItemOption.canBePickedUpBy(itemEntity.getItem(), player.getUUID())) {
            ci.cancel();
        }
    }
}
