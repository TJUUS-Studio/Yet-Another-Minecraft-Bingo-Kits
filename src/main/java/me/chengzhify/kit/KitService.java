package me.chengzhify.kit;

import me.chengzhify.config.ConfigManager;
import me.chengzhify.utils.BingoImpl;
import me.jfenn.bingo.api.BingoEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
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

import static net.minecraft.server.command.CommandManager.literal;

public class KitService {
    private static final Logger LOGGER = LoggerFactory.getLogger("yet_another_bingo_kits/KitService");
    private final ConfigManager configManager = new ConfigManager();
    private final Map<String, BaseKit> kitsById = new LinkedHashMap<>();
    private final Map<UUID, String> playerKitSelection = new HashMap<>();
    private final Map<UUID, String> queuedKitSelection = new HashMap<>();
    private final Map<UUID, String> roundPreselectedKit = new HashMap<>();
    private final Map<UUID, String> previousRoundSelection = new HashMap<>();
    private final Map<UUID, Boolean> kitGranted = new HashMap<>();
    private final Map<UUID, KitSelectionSession> sessions = new HashMap<>();
    private final Set<UUID> selectionPromptedThisRound = new HashSet<>();
    private final Map<UUID, Set<UUID>> droppedKitItemsByOwner = new HashMap<>();
    private final Map<UUID, UUID> droppedKitItemOwners = new HashMap<>();
    private final Random random = new Random();
    private boolean bingoInitialized;
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
            queuedKitSelection.clear();
            prepareNextRoundSelections();
            bingoInitialized = true;
            LOGGER.info("Received Bingo GAME_STARTING; onlinePlayers={}", server == null ? 0 : server.getPlayerManager().getPlayerList().size());
        });
        BingoEvents.GAME_STARTED.register(event -> {
            LOGGER.info("Received Bingo GAME_STARTED; onlinePlayers={}", server == null ? 0 : server.getPlayerManager().getPlayerList().size());
            if (server == null) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (BingoImpl.isInTeam(player.getUuid())) {
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
            boolean countdown = BingoImpl.isCountdown();
            boolean inTeam = BingoImpl.isInTeam(handler.player.getUuid());
            boolean selected = playerKitSelection.containsKey(handler.player.getUuid());
            boolean preselected = roundPreselectedKit.containsKey(handler.player.getUuid());
            if (bingoInitialized && countdown && inTeam && !selected && !preselected) {
                openSelection(handler.player, true, true);
            }
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                removeOwnedKitItemsFromInventory(player);
                clearOwnedDroppedKitItems(this.server, player.getUuid());
            }
            return true;
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            clearOwnedDroppedKitItems(this.server, newPlayer.getUuid());
            if (BingoImpl.isStarted() && BingoImpl.isInTeam(newPlayer.getUuid()) && playerKitSelection.containsKey(newPlayer.getUuid())) {
                kitGranted.put(newPlayer.getUuid(), false);
                giveSelectedKit(newPlayer);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(tickingServer -> {
            if (!bingoInitialized || !BingoImpl.isCountdown()) {
                return;
            }
            for (ServerPlayerEntity player : tickingServer.getPlayerManager().getPlayerList()) {
                if (!BingoImpl.isInTeam(player.getUuid())) {
                    continue;
                }
                if (!playerKitSelection.containsKey(player.getUuid())
                        && !roundPreselectedKit.containsKey(player.getUuid())
                        && !sessions.containsKey(player.getUuid())
                        && selectionPromptedThisRound.add(player.getUuid())) {
                    openSelection(player, true, true);
                }
            }
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        literal("yabkit")
                                .executes(context -> openChangeSelection(context.getSource()))
                                .then(literal("choose").executes(context -> openChangeSelection(context.getSource())))
                )
        );
    }

    private int openChangeSelection(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        if (BingoImpl.isStarted()) {
            source.sendError(Text.literal("游戏已开始，当前不允许更换职业"));
            return 0;
        }
        openSelection(player, false, false);
        return 1;
    }

    private void openSelection(ServerPlayerEntity player, boolean mandatory, boolean markPrompted) {
        if (kitsById.isEmpty()) {
            LOGGER.warn("openSelection aborted for player={} because no kits are loaded", player.getName().getString());
            player.sendMessage(Text.literal("职业配置为空，无法选择职业"), false);
            return;
        }
        if (markPrompted) {
            selectionPromptedThisRound.add(player.getUuid());
        }
        String preselectedKitId = playerKitSelection.get(player.getUuid());
        if (preselectedKitId == null) {
            preselectedKitId = roundPreselectedKit.get(player.getUuid());
        }
        if (preselectedKitId == null) {
            preselectedKitId = queuedKitSelection.get(player.getUuid());
        }
        KitSelectionSession session = new KitSelectionSession(player.getUuid(), mandatory, preselectedKitId);
        sessions.put(player.getUuid(), session);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, playerEntity) -> createScreenHandler(syncId, playerInventory, session),
                Text.literal("职业选择")
        ));
    }

    private void sendPreselectHint(ServerPlayerEntity player) {
        MutableText button = Text.literal("[点击预选职业]")
                .formatted(Formatting.GREEN, Formatting.BOLD)
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent.RunCommand("/yabkit choose")));
        player.sendMessage(Text.literal("可在开局前预选职业：")
                .formatted(Formatting.YELLOW)
                .append(button), false);
        player.sendMessage(Text.literal("命令：/yabkit choose；每次预选只会跳过下一局的职业弹窗")
                .formatted(Formatting.GRAY), false);
    }

    private ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory, KitSelectionSession session) {
        SimpleInventory inventory = new SimpleInventory(54);
        fillBackground(inventory);
        List<BaseKit> kitList = kitsById.values().stream().sorted(Comparator.comparing(BaseKit::getId)).toList();
        int slot = 10;
        for (BaseKit kit : kitList) {
            while (slot % 9 == 8) {
                slot++;
            }
            if (slot >= 44) {
                break;
            }
            session.slotKitId.put(slot, kit.getId());
            inventory.setStack(slot, createKitDisplayStack(kit));
            slot++;
        }
        inventory.setStack(49, createConfirmStack(session.pendingKitId));
        return new KitSelectionScreenHandler(syncId, playerInventory, inventory, session, this);
    }

    private ItemStack createKitDisplayStack(BaseKit kit) {
        ItemStack icon = new ItemStack(Registries.ITEM.get(Identifier.of(kit.getIconId())));
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(kit.getName()).formatted(Formatting.GOLD));
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal(kit.getDescription()).formatted(Formatting.GRAY));
        lore.add(Text.empty());
        lore.add(Text.literal("装备清单").formatted(Formatting.YELLOW));
        for (KitItemEntry entry : kit.getItems()) {
            lore.add(Text.literal("• " + entry.asDisplayText()).formatted(Formatting.AQUA));
        }
        icon.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return icon;
    }

    private ItemStack createConfirmStack(String selectedKitId) {
        ItemStack confirm = new ItemStack(Registries.ITEM.get(Identifier.of("minecraft:lime_wool")));
        String text = selectedKitId == null ? "点击职业后再确认" : "确认职业: " + kitsById.get(selectedKitId).getName();
        confirm.set(DataComponentTypes.CUSTOM_NAME, Text.literal(text).formatted(Formatting.GREEN));
        confirm.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("先点击一个职业，再点此格确认").formatted(Formatting.GRAY)
        )));
        return confirm;
    }

    private void fillBackground(Inventory inventory) {
        ItemStack filler = new ItemStack(Registries.ITEM.get(Identifier.of("minecraft:gray_stained_glass_pane")));
        filler.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < inventory.size(); i++) {
            inventory.setStack(i, filler.copy());
        }
    }

    private void onKitClicked(KitSelectionSession session, SimpleInventory inventory, int slot) {
        String kitId = session.slotKitId.get(slot);
        if (kitId == null) {
            return;
        }
        session.pendingKitId = kitId;
        inventory.setStack(49, createConfirmStack(kitId));
    }

    private void onConfirmClicked(ServerPlayerEntity player, KitSelectionSession session) {
        if (session.pendingKitId == null) {
            return;
        }
        session.completed = true;
        sessions.remove(player.getUuid());
        if (session.mandatory) {
            playerKitSelection.put(player.getUuid(), session.pendingKitId);
            queuedKitSelection.remove(player.getUuid());
            roundPreselectedKit.remove(player.getUuid());
            player.sendMessage(Text.literal("你已选择职业: " + kitsById.get(session.pendingKitId).getName() + "，装备将在开局时发放").formatted(Formatting.GREEN), false);
        } else {
            queuedKitSelection.put(player.getUuid(), session.pendingKitId);
            roundPreselectedKit.remove(player.getUuid());
            player.sendMessage(Text.literal("已预选职业: " + kitsById.get(session.pendingKitId).getName() + "；下一局将直接使用该职业并跳过弹窗，可再次用 /yabkit choose 修改").formatted(Formatting.YELLOW), false);
        }
        player.closeHandledScreen();
    }

    private void onSelectionClosed(ServerPlayerEntity player, KitSelectionSession session) {
        sessions.remove(player.getUuid());
    }

    private void giveSelectedKit(ServerPlayerEntity player) {
        if (!BingoImpl.isStarted() || !BingoImpl.isInTeam(player.getUuid())) {
            return;
        }
        if (kitGranted.getOrDefault(player.getUuid(), false)) {
            return;
        }
        String kitId = playerKitSelection.get(player.getUuid());
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
            KitItemOption.assignOwner(stack, player.getUuid());
            player.getInventory().offerOrDrop(stack);
        }
        kitGranted.put(player.getUuid(), true);
    }

    private void clearOwnedDroppedKitItems(MinecraftServer currentServer, UUID ownerId) {
        if (currentServer == null || ownerId == null) {
            return;
        }
        for (ServerWorld world : currentServer.getWorlds()) {
            world.iterateEntities().forEach(entity -> {
                if (entity instanceof ItemEntity itemEntity && KitItemOption.isOwnedBy(itemEntity.getStack(), ownerId)) {
                    itemEntity.discard();
                }
            });
        }
    }

    private void removeOwnedKitItemsFromInventory(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (KitItemOption.isOwnedBy(stack, player.getUuid())) {
                inventory.setStack(i, ItemStack.EMPTY);
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
    }

    private void resetRoundData() {
        prepareNextRoundSelections();
        roundPreselectedKit.clear();
        bingoInitialized = false;
    }

    private void finalizeSelection(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        if (playerKitSelection.containsKey(playerId)) {
            return;
        }

        KitSelectionSession session = sessions.remove(playerId);
        String fallbackKitId = session != null ? session.pendingKitId : null;
        if (fallbackKitId != null && !kitsById.containsKey(fallbackKitId)) {
            fallbackKitId = null;
        }
        if (fallbackKitId == null) {
            fallbackKitId = roundPreselectedKit.remove(playerId);
        }
        if (fallbackKitId == null) {
            fallbackKitId = previousRoundSelection.get(playerId);
        }
        if (fallbackKitId == null || !kitsById.containsKey(fallbackKitId)) {
            fallbackKitId = kitsById.keySet().stream().findFirst().orElse(null);
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
        player.sendMessage(Text.literal("未在倒计时结束前完成职业选择，已自动选择: " + kitsById.get(fallbackKitId).getName()).formatted(Formatting.YELLOW), false);
        if (player.currentScreenHandler != null) {
            player.closeHandledScreen();
        }
    }

    private static class KitSelectionSession {
        private final UUID playerId;
        private final boolean mandatory;
        private final Map<Integer, String> slotKitId = new HashMap<>();
        private String pendingKitId;
        private boolean completed;

        private KitSelectionSession(UUID playerId, boolean mandatory, String preselectedKitId) {
            this.playerId = playerId;
            this.mandatory = mandatory;
            this.pendingKitId = preselectedKitId;
            this.completed = false;
        }
    }

    private static class KitSelectionScreenHandler extends GenericContainerScreenHandler {
        private final SimpleInventory inventory;
        private final KitSelectionSession session;
        private final KitService kitService;

        protected KitSelectionScreenHandler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, KitSelectionSession session, KitService kitService) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
            this.inventory = inventory;
            this.session = session;
            this.kitService = kitService;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }
            if (slotIndex >= 0 && slotIndex < 54) {
                if (session.slotKitId.containsKey(slotIndex)) {
                    kitService.onKitClicked(session, inventory, slotIndex);
                    return;
                }
                if (slotIndex == 49) {
                    kitService.onConfirmClicked(serverPlayer, session);
                    return;
                }
                return;
            }
            super.onSlotClick(slotIndex, button, actionType, player);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void onClosed(PlayerEntity player) {
            super.onClosed(player);
            if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.getUuid().equals(session.playerId)) {
                kitService.onSelectionClosed(serverPlayer, session);
            }
        }
    }
}
