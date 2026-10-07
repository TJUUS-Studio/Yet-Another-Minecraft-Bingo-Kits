package me.chengzhify;

import com.google.gson.Gson;
import com.mojang.serialization.JsonOps;
import me.chengzhify.config.KitConfigFile;
import me.chengzhify.kit.KitItemOption;
import me.chengzhify.utils.BingoImpl;
import me.jfenn.bingo.api.BingoApi;
import me.jfenn.bingo.api.BingoEvents;
import me.jfenn.bingo.api.event.GameStartedEvent;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public class KitsGameTest {
    private static final Gson GSON = new Gson();

    @GameTest
    public void defaultKitItems(GameTestHelper helper) throws Exception {
        validateKitResource(helper, "/yet_another_bingo_kits/default_kits.json");
        helper.succeed();
    }

    @GameTest
    public void mainBranchKitPreset(GameTestHelper helper) throws Exception {
        validateKitResource(helper, "/yet_another_bingo_kits/kits.json");
        helper.succeed();
    }

    private static void validateKitResource(GameTestHelper helper, String resource) throws Exception {
        var server = helper.getLevel().getServer();
        try (var reader = new InputStreamReader(KitsGameTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8)) {
            var config = GSON.fromJson(reader, KitConfigFile.class);
            helper.assertFalse(config.kits.isEmpty(), "Default kits must be present");
            for (var kit : config.kits) {
                helper.assertTrue(BuiltInRegistries.ITEM.getOptional(Identifier.parse(kit.iconId)).isPresent(),
                        "Unknown icon for " + kit.id);
                for (var entry : kit.items) {
                    for (var option : entry.options) {
                        var stack = new KitItemOption(option.itemId, option.count, option.nbt, option.components,
                                option.shareable, option.enchantments, option.displayName).createStack(server);
                        helper.assertFalse(stack.isEmpty(), "Empty kit item: " + option.itemId);
                        helper.assertValueEqual(stack.getCount(), option.count, "Kit item count");
                        helper.assertValueEqual(KitItemOption.isKitTagged(stack), !option.shareable, "Item binding");
                        for (var enchantment : option.enchantments.entrySet()) {
                            var holder = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                                    .get(Identifier.parse(enchantment.getKey())).orElseThrow();
                            helper.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(holder),
                                    enchantment.getValue(), "Enchantment: " + enchantment.getKey());
                        }
                        for (var component : option.components.entrySet()) {
                            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(component.getKey()));
                            var expected = type.codecOrThrow().parse(server.registryAccess()
                                    .createSerializationContext(JsonOps.INSTANCE), GSON.toJsonTree(component.getValue())).getOrThrow();
                            helper.assertValueEqual(stack.get(type), expected, "Component: " + component.getKey());
                        }
                    }
                }
            }
        }
    }

    @GameTest
    public void containerRestrictions(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var bound = option(false).createStack(helper.getLevel().getServer());
        var shareable = option(true).createStack(helper.getLevel().getServer());
        var chestSlot = new Slot(new SimpleContainer(1), 0, 0, 0);
        var inventorySlot = new Slot(player.getInventory(), 0, 0, 0);
        helper.assertFalse(chestSlot.mayPlace(bound), "Bound kit items must not enter a container");
        helper.assertTrue(inventorySlot.mayPlace(bound), "Bound kit items must remain usable in player inventory");
        helper.assertTrue(chestSlot.mayPlace(shareable), "Shareable kit items must enter containers");
        helper.assertTrue(chestSlot.mayPlace(new ItemStack(Items.DIAMOND)), "Ordinary items must enter containers");

        var legacyTag = new CompoundTag();
        legacyTag.putBoolean("yabk_kit_item", true);
        legacyTag.putBoolean("yabk_no_container", true);
        legacyTag.putString("yabk_owner", player.getUUID().toString());
        var legacy = new ItemStack(Items.STONE);
        legacy.set(DataComponents.CUSTOM_DATA, CustomData.of(legacyTag));
        helper.assertFalse(chestSlot.mayPlace(legacy), "Legacy bound items must retain container restrictions");
        helper.assertTrue(KitItemOption.isOwnedBy(legacy, player.getUUID()), "Legacy owner must be recognized");
        helper.succeed();
    }

    @GameTest
    public void ownerOnlyPickup(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var stack = option(false).createStack(helper.getLevel().getServer());
        KitItemOption.assignOwner(stack, UUID.randomUUID());
        var entity = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), stack);
        entity.setNoPickUpDelay();
        entity.playerTouch(player);
        helper.assertFalse(entity.isRemoved(), "Other players must not pick up bound kit items");
        helper.assertValueEqual(itemCount(player, Items.STONE), 0, "Other player's inventory must stay empty");
        KitItemOption.assignOwner(entity.getItem(), player.getUUID());
        entity.playerTouch(player);
        helper.assertTrue(entity.isRemoved(), "The owner must be able to pick up the bound item");
        helper.assertValueEqual(itemCount(player, Items.STONE), 1, "Owner must receive the bound item");
        helper.succeed();
    }

    @GameTest
    public void legacyNbtAndCustomComponents(GameTestHelper helper) {
        var stack = new KitItemOption("minecraft:diamond_sword", 1,
                "{Damage:7,Enchantments:[{id:\"minecraft:sharpness\",lvl:3}]}",
                Map.of("minecraft:custom_data", Map.of("custom_marker", "preserved")), false,
                Map.of(), "Custom sword").createStack(helper.getLevel().getServer());
        helper.assertValueEqual(stack.getDamageValue(), 7, "Legacy damage");
        var sharpness = helper.getLevel().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(Identifier.parse("minecraft:sharpness")).orElseThrow();
        helper.assertValueEqual(EnchantmentHelper.getItemEnchantmentLevel(sharpness, stack), 3, "Legacy enchantment");
        helper.assertValueEqual(stack.getCustomName().getString(), "Custom sword", "Custom name");
        helper.assertValueEqual(stack.get(DataComponents.CUSTOM_DATA).copyTag().getStringOr("custom_marker", ""),
                "preserved", "Binding must preserve custom component data");
        helper.assertTrue(KitItemOption.shouldBlockContainerInsert(stack), "Custom components must retain binding");
        helper.succeed();
    }

    @GameTest
    public void selectionAndRoundLifecycle(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var api = BingoApi.getINSTANCE();
        helper.assertTrue(api != null && BingoImpl.isAvailable(), "Yet Another Bingo must initialize its public API");
        Field stateField = api.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        var state = stateField.get(api);
        Field phaseField = state.getClass().getDeclaredField("state");
        phaseField.setAccessible(true);
        var originalPhase = (Enum<?>) phaseField.get(state);
        var player = helper.makeMockServerPlayerInLevel();
        setPhase(state, phaseField, originalPhase, "PREGAME");
        server.getCommands().getDispatcher().execute("join red", player.createCommandSourceStack());
        helper.runAfterDelay(1, () -> {
            try {
                runRoundLifecycle(helper, player, state, phaseField, originalPhase);
                helper.succeed();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private static void runRoundLifecycle(GameTestHelper helper, ServerPlayer player, Object state,
                                          Field phaseField, Enum<?> originalPhase) throws Exception {
        var server = helper.getLevel().getServer();
        var api = BingoApi.getINSTANCE();
        try {
            helper.assertTrue(BingoImpl.isInTeam(player.getUUID()), "Mock player must join a Bingo team");
            setPhase(state, phaseField, originalPhase, "PREGAME");
            BingoEvents.GAME_RESET.invoke(null);
            server.getCommands().getDispatcher().execute("kit", player.createCommandSourceStack());
            helper.assertTrue(player.containerMenu instanceof ChestMenu, "Choose command must open the kit menu");
            var menu = player.containerMenu;
            menu.clicked(10, 0, ContainerInput.PICKUP, player);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "One click must select the kit and close the menu");
            helper.assertValueEqual(boundItemCount(player), 0, "Preselection must not give items before game start");
            server.getCommands().getDispatcher().execute("kit", player.createCommandSourceStack());
            var selectedIcon = player.containerMenu.getSlot(10).getItem();
            helper.assertTrue(selectedIcon.get(DataComponents.LORE).lines().stream()
                    .anyMatch(line -> line.getString().equals("已选择")), "Reopening /kit must highlight the preselected kit");
            helper.assertFalse(selectedIcon.getCustomName().getStyle().isItalic(), "Menu names must preserve main's non-italic style");
            player.closeContainer();

            setPhase(state, phaseField, originalPhase, "STARTING");
            BingoEvents.GAME_STARTING.invoke(null);
            helper.assertTrue(BingoImpl.isStarting(), "Public STARTING status must be recognized");
            helper.assertFalse(BingoImpl.isCountdown(), "World loading must not be treated as countdown");
            helper.assertValueEqual(server.getCommands().getDispatcher().execute("kit", player.createCommandSourceStack()),
                    0, "/kit must reject changes during STARTING");
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "A rejected /kit command must not open a menu");
            setPhase(state, phaseField, originalPhase, "COUNTDOWN");
            helper.assertTrue(BingoImpl.isCountdown(), "Bingo 2.14 countdown must be recognized");
            ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Preselection must skip the countdown menu");

            setPhase(state, phaseField, originalPhase, "PLAYING");
            BingoEvents.GAME_STARTED.invoke(new GameStartedEvent(api.getGame().getId()));
            int grantedCount = boundItemCount(player);
            helper.assertTrue(grantedCount > 0, "Game start must grant the selected kit");
            BingoEvents.GAME_STARTED.invoke(new GameStartedEvent(api.getGame().getId()));
            helper.assertValueEqual(boundItemCount(player), grantedCount, "Repeated game-start event must not duplicate kit items");

            var dropPos = helper.absolutePos(new BlockPos(1, 1, 1));
            var dropped = new ItemEntity(helper.getLevel(), dropPos.getX(), dropPos.getY(), dropPos.getZ(),
                    option(false).createStack(server));
            KitItemOption.assignOwner(dropped.getItem(), player.getUUID());
            helper.assertTrue(helper.getLevel().addFreshEntity(dropped), "Dropped item must spawn in the loaded test area");
            player.getInventory().setItem(35, new ItemStack(Items.DIAMOND));
            ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(player, player.damageSources().generic(), 1000);
            helper.assertValueEqual(boundItemCount(player), 0, "Death must remove bound kit items");
            helper.assertValueEqual(itemCount(player, Items.DIAMOND), 1, "Death cleanup must preserve ordinary items");
            helper.assertTrue(dropped.isRemoved(), "Death must remove dropped items owned by the player");
            ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(player, player, false);
            helper.assertValueEqual(boundItemCount(player), grantedCount, "Respawn must grant the kit again");

            // Preselection is consumed after one round; leaving the new menu open must auto-finalize.
            BingoEvents.GAME_RESET.invoke(null);
            setPhase(state, phaseField, originalPhase, "STARTING");
            BingoEvents.GAME_STARTING.invoke(null);
            setPhase(state, phaseField, originalPhase, "COUNTDOWN");
            ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
            helper.assertTrue(player.containerMenu instanceof ChestMenu, "Next round must prompt for selection again");
            player.getInventory().clearContent();
            setPhase(state, phaseField, originalPhase, "PLAYING");
            BingoEvents.GAME_STARTED.invoke(new GameStartedEvent(api.getGame().getId()));
            helper.assertTrue(boundItemCount(player) > 0, "Unconfirmed selection must receive a fallback kit");
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "Fallback must close the kit menu");
        } finally {
            phaseField.set(state, originalPhase);
            BingoEvents.GAME_RESET.invoke(null);
            server.getPlayerList().getPlayers().remove(player);
            player.discard();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setPhase(Object state, Field phaseField, Enum<?> original, String name) throws Exception {
        phaseField.set(state, Enum.valueOf((Class) original.getDeclaringClass(), name));
    }

    private static KitItemOption option(boolean shareable) {
        return new KitItemOption("minecraft:stone", 1, "", Map.of(), shareable, Map.of(), "");
    }

    private static int itemCount(ServerPlayer player, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    private static int boundItemCount(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (KitItemOption.isOwnedBy(stack, player.getUUID())) count += stack.getCount();
        }
        return count;
    }
}
