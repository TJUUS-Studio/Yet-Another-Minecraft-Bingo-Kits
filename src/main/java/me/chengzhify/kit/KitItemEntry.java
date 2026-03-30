package me.chengzhify.kit;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class KitItemEntry {
    private final List<KitItemOption> options;

    public KitItemEntry(List<KitItemOption> options) {
        this.options = options;
    }

    public ItemStack createStack(MinecraftServer server, Random random) {
        if (options.isEmpty()) {
            return ItemStack.EMPTY;
        }
        KitItemOption option = options.get(options.size() == 1 ? 0 : random.nextInt(options.size()));
        return option.createStack(server);
    }

    public List<KitItemOption> getOptions() {
        return options;
    }

    public String asDisplayText() {
        return options.stream().map(KitItemOption::asDisplayText).collect(Collectors.joining(" 或 "));
    }
}
