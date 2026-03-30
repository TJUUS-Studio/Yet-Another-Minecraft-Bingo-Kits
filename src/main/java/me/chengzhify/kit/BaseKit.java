package me.chengzhify.kit;

import java.util.List;

public class BaseKit {
    private final String id;
    private final String name;
    private final String description;
    private final String iconId;
    private final List<KitItemEntry> items;

    public BaseKit(String id, String name, String description, String iconId, List<KitItemEntry> items) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.iconId = iconId;
        this.items = items;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIconId() { return iconId; }
    public List<KitItemEntry> getItems() { return items; }
}
