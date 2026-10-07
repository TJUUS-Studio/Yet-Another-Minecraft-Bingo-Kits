package me.chengzhify.kit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KitItemOption {
    private static final Gson GSON = new Gson();
    public static final String KIT_ITEM_MARKER_KEY = "yet_another_bingo_kits:bound_kit_item";
    public static final String KIT_ITEM_OWNER_KEY = "yet_another_bingo_kits:bound_kit_owner";
    public static final String KIT_ITEM_NO_CONTAINER_KEY = "yet_another_bingo_kits:block_container_insert";
    private static final String LEGACY_KIT_ITEM_MARKER_KEY = "yabk_kit_item";
    private static final String LEGACY_KIT_ITEM_OWNER_KEY = "yabk_owner";
    private static final String LEGACY_KIT_ITEM_NO_CONTAINER_KEY = "yabk_no_container";
    private final String itemId;
    private final int count;
    private final String nbt;
    private final Map<String, Object> components;
    private final boolean shareable;
    private final Map<String, Integer> enchantments;
    private final String displayName;

    public KitItemOption(String itemId, int count, String nbt, Map<String, Object> components, boolean shareable, Map<String, Integer> enchantments, String displayName) {
        this.itemId = itemId;
        this.count = Math.max(1, count);
        this.nbt = nbt;
        this.components = components;
        this.shareable = shareable;
        this.enchantments = enchantments;
        this.displayName = displayName;
    }

    public ItemStack createStack(MinecraftServer server) {
        ItemStack stack = createBaseStack(server);
        if (!shareable) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(createMarkerData(stack)));
            applyKitTooltip(stack);
        }
        if (displayName != null && !displayName.isBlank()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName));
        }
        if (enchantments != null && !enchantments.isEmpty()) {
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                Holder.Reference<Enchantment> enchantment = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .get(Identifier.parse(entry.getKey())).orElse(null);
                if (enchantment != null) {
                    stack.enchant(enchantment, entry.getValue());
                }
            }
        }
        return stack;
    }

    private ItemStack createBaseStack(MinecraftServer server) {
        Identifier identifier = Identifier.parse(itemId);
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        ItemStack stack = new ItemStack(item, count);
        applyLegacyNbt(stack, server);
        applyComponents(stack, server);
        return stack;
    }

    private CompoundTag createMarkerData(ItemStack stack) {
        CompoundTag marker = stack.get(DataComponents.CUSTOM_DATA) != null
                ? stack.get(DataComponents.CUSTOM_DATA).copyTag()
                : new CompoundTag();
        marker.putBoolean(KIT_ITEM_MARKER_KEY, true);
        marker.putBoolean(KIT_ITEM_NO_CONTAINER_KEY, true);
        return marker;
    }

    private void applyLegacyNbt(ItemStack stack, MinecraftServer server) {
        if (nbt == null || nbt.isBlank()) {
            return;
        }
        try {
            CompoundTag parsed = TagParser.parseCompoundFully(nbt);
            if (parsed.contains("StoredEnchantments")) {
                parsed.getList("StoredEnchantments").ifPresent(enchantmentsList -> applyEnchantments(stack, server, enchantmentsList));
            }
            if (parsed.contains("Enchantments")) {
                parsed.getList("Enchantments").ifPresent(enchantmentsList -> applyEnchantments(stack, server, enchantmentsList));
            }
            if (parsed.contains("Damage")) {
                stack.setDamageValue(parsed.getInt("Damage").orElse(0));
            }
        } catch (Exception ignored) {
        }
    }

    private void applyEnchantments(ItemStack stack, MinecraftServer server, ListTag enchantmentList) {
        for (int i = 0; i < enchantmentList.size(); i++) {
            CompoundTag enchantmentData = enchantmentList.getCompoundOrEmpty(i);
            String enchantmentId = enchantmentData.getStringOr("id", "");
            int level = enchantmentData.getInt("lvl").orElse(0);
            if (enchantmentId.isBlank() || level <= 0) {
                continue;
            }
            Holder.Reference<Enchantment> enchantment = server.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .get(Identifier.parse(enchantmentId))
                    .orElse(null);
            if (enchantment != null) {
                stack.enchant(enchantment, level);
            }
        }
    }

    private void applyComponents(ItemStack stack, MinecraftServer server) {
        if (components == null || components.isEmpty()) {
            return;
        }
        var jsonOps = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (Map.Entry<String, Object> entry : components.entrySet()) {
            Identifier identifier = Identifier.tryParse(entry.getKey());
            if (identifier == null) {
                continue;
            }
            var componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier);
            if (componentType == null) {
                continue;
            }
            Object rawValue = entry.getValue();
            if (rawValue == null) {
                stack.remove(componentType);
                continue;
            }
            JsonElement jsonElement = GSON.toJsonTree(rawValue);
            try {
                applyComponent(stack, componentType, jsonOps, jsonElement);
            } catch (Throwable ignored) {
            }
        }
    }

    private static <T> void applyComponent(ItemStack stack, DataComponentType<T> type,
                                           RegistryOps<JsonElement> ops, JsonElement value) {
        stack.set(type, type.codecOrThrow().parse(ops, value).getOrThrow());
    }

    private void applyKitTooltip(ItemStack stack) {
        List<Component> loreLines = new ArrayList<>();
        ItemLore existingLore = stack.get(DataComponents.LORE);
        if (existingLore != null) {
            loreLines.addAll(existingLore.lines());
            if (!loreLines.isEmpty()) {
                loreLines.add(Component.empty());
            }
        }
        loreLines.add(Component.literal("职业装备").withStyle(ChatFormatting.GOLD));
        loreLines.add(Component.literal("死亡后删除并重发").withStyle(ChatFormatting.GRAY));
        loreLines.add(Component.literal("不可放入容器").withStyle(ChatFormatting.DARK_GRAY));
        stack.set(DataComponents.LORE, new ItemLore(loreLines));
    }

    public String getItemId() {
        return itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String asDisplayText() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName + " x" + count;
        }
        return itemId + " x" + count;
    }

    public static boolean isKitTagged(ItemStack stack) {
        return getMarkerFlag(stack, KIT_ITEM_MARKER_KEY);
    }

    public static boolean shouldBlockContainerInsert(ItemStack stack) {
        return getMarkerFlag(stack, KIT_ITEM_NO_CONTAINER_KEY);
    }

    public static void assignOwner(ItemStack stack, UUID ownerId) {
        if (!isKitTagged(stack) || ownerId == null) {
            return;
        }
        CompoundTag marker = stack.get(DataComponents.CUSTOM_DATA) != null
                ? stack.get(DataComponents.CUSTOM_DATA).copyTag()
                : new CompoundTag();
        marker.putString(KIT_ITEM_OWNER_KEY, ownerId.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
    }

    public static UUID getOwner(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag marker = customData.copyTag();
        String rawOwner = marker.getStringOr(KIT_ITEM_OWNER_KEY, "");
        if (rawOwner.isBlank()) {
            rawOwner = marker.getStringOr(LEGACY_KIT_ITEM_OWNER_KEY, "");
        }
        if (rawOwner.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawOwner);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean canBePickedUpBy(ItemStack stack, UUID playerId) {
        if (!isKitTagged(stack)) {
            return true;
        }
        UUID ownerId = getOwner(stack);
        return ownerId != null && ownerId.equals(playerId);
    }

    public static boolean isOwnedBy(ItemStack stack, UUID ownerId) {
        if (ownerId == null || !isKitTagged(stack)) {
            return false;
        }
        UUID taggedOwner = getOwner(stack);
        return ownerId.equals(taggedOwner);
    }

    private static boolean getMarkerFlag(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        CompoundTag marker = customData.copyTag();
        if (marker.getBoolean(key).orElse(false)) {
            return true;
        }
        return switch (key) {
            case KIT_ITEM_MARKER_KEY -> marker.getBoolean(LEGACY_KIT_ITEM_MARKER_KEY).orElse(false);
            case KIT_ITEM_NO_CONTAINER_KEY -> marker.getBoolean(LEGACY_KIT_ITEM_NO_CONTAINER_KEY).orElse(false);
            default -> false;
        };
    }
}
