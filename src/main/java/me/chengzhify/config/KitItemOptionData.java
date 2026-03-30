package me.chengzhify.config;

import java.util.HashMap;
import java.util.Map;

public class KitItemOptionData {
    public String itemId = "minecraft:stone";
    public int count = 1;
    public String nbt = "";
    public Map<String, Object> components = new HashMap<>();
    public boolean shareable = false;
    public Map<String, Integer> enchantments = new HashMap<>();
    public String displayName = "";
}
