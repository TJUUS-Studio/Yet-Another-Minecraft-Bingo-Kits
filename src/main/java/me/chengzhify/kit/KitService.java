package me.chengzhify.kit;

import me.chengzhify.config.ConfigManager;
import me.chengzhify.utils.BingoImpl;
import me.jfenn.bingo.api.BingoEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.literal;

public class KitService {
    private static final Logger LOGGER = LoggerFactory.getLogger("yet_another_bingo_kits/KitService");
    private static final int[] KIT_DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final ConfigManager configManager = new ConfigManager();
    private final Map<String, BaseKit> kitsById = new LinkedHashMap<>();
    private final Map<UUID, String> playerKitSelection = new HashMap<>();
    private final Map<UUID, String> queuedKitSelection = new HashMap<>();
    private final Map<UUID, String> roundPreselectedKit = new HashMap<>();
    private final Map<UUID, String> previousRoundSelection = new HashMap<>();
    private final Map<UUID, Boolean> kitGranted = new HashMap<>();
    private final Map<UUID, KitSelectionSession> sessions = new HashMap<>();
    private final Set<UUID> selectionPromptedThisRound = new HashSet<>();
    private final Random random = new Random();
    private boolean bingoInitialized;
    private boolean countdownSelectionPromptReady;
    private MinecraftServer server;

    public void initialize() {
        configManager.load();
        for (BaseKit kit : configManager.getKits()) {
            kitsById.put(kit.getId(), kit);
        }
        LOGGER.info("Initialized kit service with {} kits: {}", kitsById.size(), kitsById.keySet());
        registerLifecycle();
        registerBingoEvents();
        registerFabricEvents();
        registerCommands();
    }

    private void registerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> {
            this.server = startedServer;
            LOGGER.info("Server started");
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stoppedServer -> {
            LOGGER.info("Server stopped");
            this.server = null;
        });
    }

    private void registerBingoEvents() {
        BingoEvents.INIT.register(event -> {
            resetRoundData();
            bingoInitialized = true;
            LOGGER.info("Received Bingo INIT; available={}, starting={}, started={}", BingoImpl.isAvailable(), BingoImpl.isStarting(), BingoImpl.isStarted());
        });
        BingoEvents.GAME_STARTING.register(v -> {
            roundPreselectedKit.clear();
            roundPreselectedKit.putAll(queuedKitSelection);
            prepareNextRoundSelections();
            countdownSelectionPromptReady = false;
            bingoInitialized = true;
            LOGGER.info("Received Bingo GAME_STARTING; onlinePlayers={}, queuedPreselects={}", server == null ? 0 : server.getPlayerList().getPlayers().size(), roundPreselectedKit.size());
        });
        BingoEvents.GAME_STARTED.register(event -> {
            LOGGER.info("Received Bingo GAME_STARTED; onlinePlayers={}", server == null ? 0 : server.getPlayerList().getPlayers().size());
            if (server == null) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (BingoImpl.isInTeam(player.getUUID())) {
                    finalizeSelection(player);
                    giveSelectedKit(player);
                }
            }
            sessions.clear();
        });
        BingoEvents.GAME_ENDED.register(event -> {
            LOGGER.info("Received Bingo GAME_ENDED");
            resetRoundData();
        });
        BingoEvents.GAME_RESET.register(v -> {
            LOGGER.info("Received Bingo GAME_RESET");
            resetRoundData();
        });
    }

    private void registerFabricEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joinedServer) -> {
            sendPreselectHint(handler.player);
            boolean selecting = BingoImpl.isCountdown() || BingoImpl.isStarting();
            boolean inTeam = BingoImpl.isInTeam(handler.player.getUUID());
            boolean selected = playerKitSelection.containsKey(handler.player.getUUID());
            boolean preselected = roundPreselectedKit.containsKey(handler.player.getUUID());
            if (bingoInitialized && selecting && countdownSelectionPromptReady && inTeam && !selected && !preselected) {
                openSelection(handler.player, true, true);
            }
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer player) {
                removeOwnedKitItemsFromInventory(player);
                clearOwnedDroppedKitItems(this.server, player.getUUID());
            }
            return true;
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            clearOwnedDroppedKitItems(this.server, newPlayer.getUUID());
            if (BingoImpl.isStarted() && BingoImpl.isInTeam(newPlayer.getUUID()) && playerKitSelection.containsKey(newPlayer.getUUID())) {
                kitGranted.put(newPlayer.getUUID(), false);
                giveSelectedKit(newPlayer);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(tickingServer -> {
            if (!bingoInitialized) {
                return;
            }
            if (!BingoImpl.isCountdown()) {
                return;
            }
            if (!countdownSelectionPromptReady) {
                countdownSelectionPromptReady = true;
            }
            promptMissingSelections(tickingServer);
        });
    }

    private void promptMissingSelections(MinecraftServer currentServer) {
        if (currentServer == null) {
            return;
        }
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (!BingoImpl.isInTeam(playerId)) {
                continue;
            }
            if (!playerKitSelection.containsKey(playerId)
                    && !roundPreselectedKit.containsKey(playerId)
                    && !sessions.containsKey(playerId)
                    && selectionPromptedThisRound.add(playerId)) {
                openSelection(player, true, true);
            }
        }
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        literal("kit").executes(context -> openChangeSelection(context.getSource()))
                )
        );
    }

    private int openChangeSelection(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        if (BingoImpl.isStarted() || BingoImpl.isStarting() || BingoImpl.isCountdown()) {
            source.sendFailure(Component.literal("游戏已开始，当前不允许更换职业"));
            return 0;
        }
        openSelection(player, false, false);
        return 1;
    }

    private void openSelection(ServerPlayer player, boolean mandatory, boolean markPrompted) {
        if (kitsById.isEmpty()) {
            LOGGER.warn("openSelection aborted for player={} because no kits are loaded", player.getName().getString());
            player.sendSystemMessage(Component.literal("职业配置为空，无法选择职业"), false);
            return;
        }
        if (markPrompted) {
            selectionPromptedThisRound.add(player.getUUID());
        }
        String selectedKitId = resolveDisplayedSelection(player.getUUID());
        KitSelectionSession session = new KitSelectionSession(player.getUUID(), mandatory, selectedKitId);
        sessions.put(player.getUUID(), session);
        player.openMenu(new SimpleMenuProvider(
                (syncId, playerInventory, playerEntity) -> createScreenHandler(syncId, playerInventory, session),
                Component.literal("职业选择")
        ));
    }

    private String resolveDisplayedSelection(UUID playerId) {
        String selectedKitId = playerKitSelection.get(playerId);
        if (selectedKitId == null) {
            selectedKitId = roundPreselectedKit.get(playerId);
        }
        if (selectedKitId == null) {
            selectedKitId = queuedKitSelection.get(playerId);
        }
        return selectedKitId;
    }

    private void sendPreselectHint(ServerPlayer player) {
        player.sendSystemMessage(
                Component.literal("可在开局前使用 /kit 预选职业；每次预选只会跳过下一局的职业弹窗")
                        .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withItalic(false)),
                false
        );
    }

    private AbstractContainerMenu createScreenHandler(int syncId, Inventory playerInventory, KitSelectionSession session) {
        SimpleContainer inventory = new SimpleContainer(54);
        fillBackground(inventory);
        List<BaseKit> kitList = kitsById.values().stream().sorted(Comparator.comparing(BaseKit::getId)).toList();
        int index = 0;
        for (BaseKit kit : kitList) {
            if (index >= KIT_DISPLAY_SLOTS.length) {
                break;
            }
            int slot = KIT_DISPLAY_SLOTS[index++];
            session.slotKitId.put(slot, kit.getId());
            inventory.setItem(slot, createKitDisplayStack(kit, kit.getId().equals(session.selectedKitId)));
        }
        return new KitSelectionScreenHandler(syncId, playerInventory, inventory, session, this);
    }

    private ItemStack createKitDisplayStack(BaseKit kit, boolean selected) {
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(kit.getIconId())));
        icon.set(DataComponents.CUSTOM_NAME, styled(kit.getName(), ChatFormatting.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(styled(kit.getDescription(), ChatFormatting.GRAY));
        lore.add(Component.empty());
        lore.add(styled("装备清单", ChatFormatting.YELLOW));
        for (KitItemEntry entry : kit.getItems()) {
            lore.add(styled("- " + entry.asDisplayText(), ChatFormatting.AQUA));
        }
        if (selected) {
            lore.add(Component.empty());
            lore.add(styled("已选择", ChatFormatting.GREEN));
        }
        icon.set(DataComponents.LORE, new ItemLore(lore));
        return icon;
    }

    private void fillBackground(Container inventory) {
        ItemStack filler = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:gray_stained_glass_pane")));
        filler.set(DataComponents.CUSTOM_NAME, Component.literal(" ").withStyle(style -> style.withItalic(false)));
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, filler.copy());
        }
    }

    private void onKitClicked(ServerPlayer player, KitSelectionSession session, String kitId) {
        BaseKit kit = kitsById.get(kitId);
        if (kit == null) {
            return;
        }
        session.completed = true;
        session.selectedKitId = kitId;
        sessions.remove(player.getUUID());
        if (session.mandatory) {
            playerKitSelection.put(player.getUUID(), kitId);
            queuedKitSelection.remove(player.getUUID());
            roundPreselectedKit.remove(player.getUUID());
            player.sendSystemMessage(styled("你已选择职业: " + kit.getName() + "，装备将在开局时发放", ChatFormatting.GREEN), false);
        } else {
            queuedKitSelection.put(player.getUUID(), kitId);
            roundPreselectedKit.remove(player.getUUID());
            player.sendSystemMessage(styled("已预选职业: " + kit.getName() + "；下一局将直接使用该职业，可再次用 /kit 修改", ChatFormatting.YELLOW), false);
        }
        player.playSound(SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
        player.closeContainer();
    }

    private void onSelectionClosed(ServerPlayer player, KitSelectionSession session) {
        sessions.remove(player.getUUID());
    }

    private void giveSelectedKit(ServerPlayer player) {
        if (!BingoImpl.isStarted() || !BingoImpl.isInTeam(player.getUUID())) {
            return;
        }
        if (kitGranted.getOrDefault(player.getUUID(), false)) {
            return;
        }
        String kitId = playerKitSelection.get(player.getUUID());
        BaseKit kit = kitId == null ? null : kitsById.get(kitId);
        if (kit == null) {
            return;
        }
        for (KitItemEntry entry : kit.getItems()) {
            MinecraftServer currentServer = this.server;
            if (currentServer == null) {
                return;
            }
            ItemStack stack = entry.createStack(currentServer, random);
            if (stack.isEmpty()) {
                continue;
            }
            KitItemOption.assignOwner(stack, player.getUUID());
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
        kitGranted.put(player.getUUID(), true);
    }

    private void clearOwnedDroppedKitItems(MinecraftServer currentServer, UUID ownerId) {
        if (currentServer == null || ownerId == null) {
            return;
        }
        for (ServerLevel world : currentServer.getAllLevels()) {
            world.getAllEntities().forEach(entity -> {
                if (entity instanceof ItemEntity itemEntity && KitItemOption.isOwnedBy(itemEntity.getItem(), ownerId)) {
                    itemEntity.discard();
                }
            });
        }
    }

    private void removeOwnedKitItemsFromInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (KitItemOption.isOwnedBy(stack, player.getUUID())) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private void prepareNextRoundSelections() {
        if (!playerKitSelection.isEmpty()) {
            previousRoundSelection.clear();
            previousRoundSelection.putAll(playerKitSelection);
        }
        sessions.clear();
        playerKitSelection.clear();
        kitGranted.clear();
        selectionPromptedThisRound.clear();
        countdownSelectionPromptReady = false;
    }

    private void resetRoundData() {
        prepareNextRoundSelections();
        roundPreselectedKit.clear();
        bingoInitialized = false;
    }

    private void finalizeSelection(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (playerKitSelection.containsKey(playerId)) {
            return;
        }

        KitSelectionSession session = sessions.remove(playerId);
        String message = null;
        String fallbackKitId = roundPreselectedKit.remove(playerId);
        if (fallbackKitId != null && kitsById.containsKey(fallbackKitId)) {
            message = "已使用预选职业: " + kitsById.get(fallbackKitId).getName();
        } else {
            fallbackKitId = session != null ? session.selectedKitId : null;
            if (fallbackKitId != null && kitsById.containsKey(fallbackKitId)) {
                message = "已确认职业: " + kitsById.get(fallbackKitId).getName();
            } else {
                fallbackKitId = queuedKitSelection.get(playerId);
                if (fallbackKitId != null && kitsById.containsKey(fallbackKitId)) {
                    message = "已使用预选职业: " + kitsById.get(fallbackKitId).getName();
                } else {
                    fallbackKitId = previousRoundSelection.get(playerId);
                    if (fallbackKitId != null && kitsById.containsKey(fallbackKitId)) {
                        message = "已沿用上一局职业: " + kitsById.get(fallbackKitId).getName();
                    } else {
                        fallbackKitId = null;
                    }
                }
            }
        }
        if (fallbackKitId == null) {
            fallbackKitId = kitsById.keySet().stream().findFirst().orElse(null);
            if (fallbackKitId != null) {
                message = "未在倒计时结束前完成职业选择，已自动选择: " + kitsById.get(fallbackKitId).getName();
            }
        }
        if (fallbackKitId == null) {
            LOGGER.warn("Unable to auto-finalize selection for player={} because no fallback kit is available", player.getName().getString());
            return;
        }

        playerKitSelection.put(playerId, fallbackKitId);
        queuedKitSelection.remove(playerId);
        if (session != null) {
            session.completed = true;
        }
        if (message != null) {
            player.sendSystemMessage(styled(message, ChatFormatting.YELLOW), false);
        }
        if (player.containerMenu != null) {
            player.closeContainer();
        }
    }

    private static Component styled(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
    }

    private static class KitSelectionSession {
        private final UUID playerId;
        private final boolean mandatory;
        private final Map<Integer, String> slotKitId = new HashMap<>();
        private String selectedKitId;
        private boolean completed;

        private KitSelectionSession(UUID playerId, boolean mandatory, String selectedKitId) {
            this.playerId = playerId;
            this.mandatory = mandatory;
            this.selectedKitId = selectedKitId;
            this.completed = false;
        }
    }

    private static class KitSelectionScreenHandler extends ChestMenu {
        private final KitSelectionSession session;
        private final KitService kitService;

        protected KitSelectionScreenHandler(int syncId, Inventory playerInventory, SimpleContainer inventory, KitSelectionSession session, KitService kitService) {
            super(MenuType.GENERIC_9x6, syncId, playerInventory, inventory, 6);
            this.session = session;
            this.kitService = kitService;
        }

        @Override
        public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            if (slotIndex >= 0 && slotIndex < 54) {
                String kitId = session.slotKitId.get(slotIndex);
                if (kitId != null) {
                    kitService.onKitClicked(serverPlayer, session, kitId);
                }
                return;
            }
            super.clicked(slotIndex, button, actionType, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.getUUID().equals(session.playerId)) {
                kitService.onSelectionClosed(serverPlayer, session);
            }
        }
    }
}
