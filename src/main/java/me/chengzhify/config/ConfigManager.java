package me.chengzhify.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.chengzhify.kit.BaseKit;
import me.chengzhify.kit.KitFactory;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DEFAULT_KITS_JSON = """
            {
              "kits": [
                {
                  "id": "kobe",
                  "name": "劳大",
                  "description": "鞘翅机动职业，开局高机动，注意烟花有限，防止坠机！",
                  "iconId": "minecraft:elytra",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:elytra",
                          "count": 1,
                          "enchantments": {
                            "minecraft:binding_curse": 1
                          },
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:firework_rocket",
                          "count": 48,
                          "enchantments": {},
                          "displayName": "1秒烟花"
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": "avenger",
                  "name": "复仇者",
                  "description": "强大的召唤物压制与神龟药水，蜗！",
                  "iconId": "minecraft:elder_guardian_spawn_egg",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:warden_spawn_egg",
                          "count": 1,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:silverfish_spawn_egg",
                          "count": 5,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:elder_guardian_spawn_egg",
                          "count": 1,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:iron_golem_spawn_egg",
                          "count": 1,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:splash_potion",
                          "count": 1,
                          "enchantments": {},
                          "nbt": "{Potion:\\"minecraft:turtle_master\\"}",
                          "displayName": "喷溅型神龟药水"
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": "yangyouji",
                  "name": "养由基",
                  "description": "强力远程职业，高爆发弓箭，一发入魂",
                  "iconId": "minecraft:bow",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:bow",
                          "count": 1,
                          "enchantments": {
                            "minecraft:power": 4,
                            "minecraft:punch": 2
                          },
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:spectral_arrow",
                          "count": 32,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": "radagon",
                  "name": "拉达冈",
                  "description": "玛莉卡就是拉达冈：重锤与风弹，高机动与空战爆发",
                  "iconId": "minecraft:mace",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:mace",
                          "count": 1,
                          "enchantments": {
                            "minecraft:density": 5,
                            "minecraft:breach": 3
                          },
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:wind_charge",
                          "count": 12,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:enchanted_book",
                          "count": 3,
                          "nbt": "{StoredEnchantments:[{id:\\"minecraft:wind_burst\\",lvl:1}]}",
                          "displayName": "风暴附魔书"
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": "misaka",
                  "name": "弥撒卡",
                  "description": "社长的意念，哈吉米南北绿豆",
                  "iconId": "minecraft:netherite_chestplate",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:cat_spawn_egg",
                          "count": 1,
                          "enchantments": {},
                          "displayName": "哈基米"
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:lead",
                          "count": 1,
                          "enchantments": {},
                          "displayName": "SM"
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:netherite_chestplate",
                          "count": 1,
                          "enchantments": {
                            "minecraft:vanishing_curse": 1,
                            "minecraft:binding_curse": 1
                          },
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:netherite_leggings",
                          "count": 1,
                          "enchantments": {
                            "minecraft:vanishing_curse": 1,
                            "minecraft:binding_curse": 1
                          },
                          "displayName": ""
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": "wandering_knight",
                  "name": "流浪骑士",
                  "description": "骑兵机动与稳定续航，开局自带钻石矛突进。",
                  "iconId": "minecraft:saddle",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:diamond_spear",
                          "count": 1,
                          "enchantments": {
                            "minecraft:vanishing_curse": 1,
                            "minecraft:lunge": 3
                          },
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:saddle",
                          "count": 1,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:golden_apple",
                          "count": 2,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": "enchanter",
                  "name": "附魔师",
                  "description": "快速成型附魔配置，真的有人玩辅助吗",
                  "iconId": "minecraft:enchanting_table",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:enchanting_table",
                          "count": 1,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:experience_bottle",
                          "count": 128,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:lapis_lazuli",
                          "count": 32,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": "egsgg",
                  "name": "Egsgg",
                  "description": "服务器爆破者",
                  "iconId": "minecraft:tnt",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:flint_and_steel",
                          "count": 1,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:tnt",
                          "count": 32,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:iron_chestplate",
                          "count": 1,
                          "enchantments": {
                            "minecraft:blast_protection": 1,
                            "minecraft:binding_curse": 1
                          },
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:iron_leggings",
                          "count": 1,
                          "enchantments": {
                            "minecraft:blast_protection": 1,
                            "minecraft:binding_curse": 1
                          },
                          "displayName": ""
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": "phoenix",
                  "name": "不死鸟",
                  "description": "其实还是会死。",
                  "iconId": "minecraft:totem_of_undying",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:totem_of_undying",
                          "count": 1,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:golden_sword",
                          "count": 1,
                          "enchantments": {
                            "minecraft:fire_aspect": 2,
                            "minecraft:unbreaking": 3,
                            "minecraft:sharpness": 3
                          },
                          "displayName": "黄金律法大剑"
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:potion",
                          "count": 2,
                          "enchantments": {},
                          "nbt": "{Potion:\\"minecraft:strong_healing\\"}",
                          "displayName": "瞬间治疗药水 II"
                        }
                      ]
                    }
                  ]
                },
                {
                  "id": "walker",
                  "name": "逃跑者",
                  "description": "你给路达哟!!!",
                  "iconId": "minecraft:chorus_fruit",
                  "items": [
                    {
                      "options": [
                        {
                          "itemId": "minecraft:chorus_fruit",
                          "count": 32,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:potion",
                          "count": 3,
                          "enchantments": {},
                          "nbt": "{Potion:\\"minecraft:long_slow_falling\\"}",
                          "displayName": "缓降药水 (1:30)"
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:diamond_boots",
                          "count": 1,
                          "enchantments": {
                            "minecraft:feather_falling": 4
                          },
                          "displayName": ""
                        }
                      ]
                    },
                    {
                      "options": [
                        {
                          "itemId": "minecraft:iron_sword",
                          "count": 1,
                          "enchantments": {},
                          "displayName": ""
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """;
    private final Path configDir;
    private final Path modConfigPath;
    private final Path kitConfigPath;
    private final KitFactory kitFactory = new KitFactory();
    private ModConfig modConfig;
    private List<BaseKit> kits;

    public ConfigManager() {
        this.configDir = FabricLoader.getInstance().getConfigDir().resolve("yet_another_bingo_kits");
        this.modConfigPath = this.configDir.resolve("mod_config.json");
        this.kitConfigPath = this.configDir.resolve("kits.json");
    }

    public void load() {
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(modConfigPath)) {
                writeJson(modConfigPath, createDefaultModConfig());
            }
            if (!Files.exists(kitConfigPath)) {
                writeJson(kitConfigPath, createDefaultKitConfig());
            }
            modConfig = readJson(modConfigPath, ModConfig.class);
            KitConfigFile kitConfigFile = readJson(kitConfigPath, KitConfigFile.class);
            kits = new ArrayList<>();
            for (KitDefinitionData data : kitConfigFile.kits) {
                kits.add(kitFactory.create(data));
            }
        } catch (IOException exception) {
            throw new RuntimeException("Failed to load Yet Another Bingo Kits config", exception);
        }
    }

    public ModConfig getModConfig() {
        return modConfig;
    }

    public void saveModConfig() {
        try {
            Files.createDirectories(configDir);
            writeJson(modConfigPath, modConfig);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to save Yet Another Bingo Kits mod config", exception);
        }
    }

    public List<BaseKit> getKits() {
        return kits;
    }

    private <T> T readJson(Path path, Class<T> type) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            T value = GSON.fromJson(reader, type);
            if (value == null) {
                throw new IllegalStateException("Empty json at " + path);
            }
            return value;
        }
    }

    private void writeJson(Path path, Object value) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(value, writer);
        }
    }

    private ModConfig createDefaultModConfig() {
        return new ModConfig();
    }

    private KitConfigFile createDefaultKitConfig() {
        KitConfigFile configFile = GSON.fromJson(readDefaultKitConfigResource(), KitConfigFile.class);
        if (configFile == null) {
            throw new IllegalStateException("Failed to parse default kit config");
        }
        return configFile;
    }

    private String readDefaultKitConfigResource() {
        try (InputStream inputStream = ConfigManager.class.getResourceAsStream("/yet_another_bingo_kits/default_kits.json")) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing default kit config resource");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read default kit config resource", exception);
        }
    }
}
