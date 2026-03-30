package me.chengzhify.kit;

import me.chengzhify.config.KitDefinitionData;
import me.chengzhify.config.KitItemEntryData;
import me.chengzhify.config.KitItemOptionData;

import java.util.ArrayList;
import java.util.List;

public class KitFactory {
    public BaseKit create(KitDefinitionData data) {
        List<KitItemEntry> entries = new ArrayList<>();
        for (KitItemEntryData itemEntryData : data.items) {
            List<KitItemOption> options = new ArrayList<>();
            for (KitItemOptionData optionData : itemEntryData.options) {
                options.add(new KitItemOption(optionData.itemId, optionData.count, optionData.nbt, optionData.components, optionData.shareable, optionData.enchantments, optionData.displayName));
            }
            entries.add(new KitItemEntry(options));
        }
        return new BaseKit(data.id, data.name, data.description, data.iconId, entries);
    }
}
