package me.chengzhify.kit;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

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
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(createMarkerData(stack)));
            applyKitTooltip(stack);
        }
        if (displayName != null && !displayName.isBlank()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
        }
        if (enchantments != null && !enchantments.isEmpty()) {
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                RegistryEntry.Reference<net.minecraft.enchantment.Enchantment> enchantment = server.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT).getEntry(Identifier.of(entry.getKey())).orElse(null);
                if (enchantment != null) {
                    stack.addEnchantment(enchantment, entry.getValue());
                }
            }
        }
        return stack;
    }

    private ItemStack createBaseStack(MinecraftServer server) {
        Identifier identifier = Identifier.of(itemId);
        Item item = Registries.ITEM.get(identifier);
        ItemStack stack = new ItemStack(item, count);
        applyLegacyNbt(stack, server);
        applyComponents(stack, server);
        return stack;
    }

    private NbtCompound createMarkerData(ItemStack stack) {
        NbtCompound marker = stack.get(DataComponentTypes.CUSTOM_DATA) != null
                ? stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt()
                : new NbtCompound();
        marker.putBoolean(KIT_ITEM_MARKER_KEY, true);
        marker.putBoolean(KIT_ITEM_NO_CONTAINER_KEY, true);
        return marker;
    }

    private void applyLegacyNbt(ItemStack stack, MinecraftServer server) {
        if (nbt == null || nbt.isBlank()) {
            return;
        }
        try {
            NbtCompound parsed = StringNbtReader.readCompound(nbt);
            if (parsed.contains("StoredEnchantments")) {
                parsed.getList("StoredEnchantments").ifPresent(enchantmentsList -> applyEnchantments(stack, server, enchantmentsList));
            }
            if (parsed.contains("Enchantments")) {
                parsed.getList("Enchantments").ifPresent(enchantmentsList -> applyEnchantments(stack, server, enchantmentsList));
            }
            if (parsed.contains("Damage")) {
                stack.setDamage(parsed.getInt("Damage").orElse(0));
            }
        } catch (Exception ignored) {
        }
    }

    private void applyEnchantments(ItemStack stack, MinecraftServer server, NbtList enchantmentList) {
        for (int i = 0; i < enchantmentList.size(); i++) {
            NbtCompound enchantmentData = enchantmentList.getCompoundOrEmpty(i);
            String enchantmentId = enchantmentData.getString("id", "");
            int level = enchantmentData.getInt("lvl").orElse(0);
            if (enchantmentId.isBlank() || level <= 0) {
                continue;
            }
            RegistryEntry.Reference<net.minecraft.enchantment.Enchantment> enchantment = server.getRegistryManager()
                    .getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
                    .getEntry(Identifier.of(enchantmentId))
                    .orElse(null);
            if (enchantment != null) {
                stack.addEnchantment(enchantment, level);
            }
        }
    }

    private void applyComponents(ItemStack stack, MinecraftServer server) {
        if (components == null || components.isEmpty()) {
            return;
        }
        var jsonOps = server.getRegistryManager().getOps(JsonOps.INSTANCE);
        for (Map.Entry<String, Object> entry : components.entrySet()) {
            Identifier identifier = Identifier.tryParse(entry.getKey());
            if (identifier == null) {
                continue;
            }
            var componentType = Registries.DATA_COMPONENT_TYPE.get(identifier);
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
                Object component = componentType.getCodecOrThrow()
                        .decode(jsonOps, jsonElement)
                        .getOrThrow()
                        .getFirst();
                stack.set((ComponentType<Object>) componentType, component);
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyKitTooltip(ItemStack stack) {
        List<Text> loreLines = new ArrayList<>();
        LoreComponent existingLore = stack.get(DataComponentTypes.LORE);
        if (existingLore != null) {
            loreLines.addAll(existingLore.lines());
            if (!loreLines.isEmpty()) {
                loreLines.add(Text.empty());
            }
        }
        loreLines.add(Text.literal("职业装备").formatted(Formatting.GOLD));
        loreLines.add(Text.literal("死亡后删除并重发").formatted(Formatting.GRAY));
        loreLines.add(Text.literal("不可放入容器").formatted(Formatting.DARK_GRAY));
        stack.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
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
        NbtCompound marker = stack.get(DataComponentTypes.CUSTOM_DATA) != null
                ? stack.get(DataComponentTypes.CUSTOM_DATA).copyNbt()
                : new NbtCompound();
        marker.putString(KIT_ITEM_OWNER_KEY, ownerId.toString());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(marker));
    }

    public static UUID getOwner(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        NbtCompound marker = customData.copyNbt();
        String rawOwner = marker.getString(KIT_ITEM_OWNER_KEY, "");
        if (rawOwner.isBlank()) {
            rawOwner = marker.getString(LEGACY_KIT_ITEM_OWNER_KEY, "");
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
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        NbtCompound marker = customData.copyNbt();
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
