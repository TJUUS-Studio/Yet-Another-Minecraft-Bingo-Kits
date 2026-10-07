package me.chengzhify.mixin;

import me.chengzhify.kit.KitItemOption;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow @Final public Container container;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void yabbingo$blockTaggedKitItemsInContainers(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (KitItemOption.shouldBlockContainerInsert(stack) && !(container instanceof Inventory)) {
            cir.setReturnValue(false);
        }
    }
}
