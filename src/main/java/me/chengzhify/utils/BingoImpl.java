package me.chengzhify.utils;

import me.jfenn.bingo.api.BingoApi;
import me.jfenn.bingo.api.data.BingoGame;
import me.jfenn.bingo.api.data.BingoGameStatus;
import me.jfenn.bingo.api.data.IBingoTeam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class BingoImpl {

    public static boolean isAvailable() {
        return BingoApi.getGame() != null && BingoApi.getTeams() != null;
    }

    public static boolean isInTeam(UUID uuid) {
        if (!isAvailable()) return false;

        for (IBingoTeam team : BingoApi.getTeams()) {
            for (UUID members : team.getPlayers()) {
                if (members.equals(uuid)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getTeamId(UUID uuid) {
        if (!isAvailable()) return null;

        for (IBingoTeam team : BingoApi.getTeams()) {
            for (UUID members : team.getPlayers()) {
                if (members.equals(uuid)) {
                    return team.getId();
                }
            }
        }
        return null;
    }

    public static boolean isStarted() {
        BingoGame game = BingoApi.getGame();
        if (game != null) {
            return game.getStatus().equals(BingoGameStatus.PLAYING);
        }
        return false;
    }

    public static boolean isStarting() {
        BingoGame game = BingoApi.getGame();
        if (game != null) {
            return game.getStatus().equals(BingoGameStatus.STARTING);
        }
        return false;
    }

    public static boolean isCountdown() {
        return "COUNTDOWN".equals(getInternalStateName());
    }

    public static int getCountdownSeconds() {
        Object api = getCurrentApi();
        if (api == null) {
            return 0;
        }
        Object config = invokeGetter(api, "getConfig");
        if (config == null) {
            return 0;
        }
        Integer value = invokeIntGetter(config, "getCountdownSeconds");
        return value == null ? 0 : Math.max(0, value);
    }

    public static int getCountdownDelayTicks() {
        Object api = getCurrentApi();
        if (api == null) {
            return 0;
        }
        Object config = invokeGetter(api, "getConfig");
        if (config == null) {
            return 0;
        }
        Integer value = invokeIntGetter(config, "getCountdownDelayTicks");
        return value == null ? 0 : Math.max(0, value);
    }

    private static String getInternalStateName() {
        Object api = getCurrentApi();
        if (api == null) {
            return null;
        }
        Object state = getFieldValue(api, "state");
        if (state == null) {
            return null;
        }
        Object gameState = getFieldValue(state, "state");
        if (gameState instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return null;
    }

    private static Object getCurrentApi() {
        try {
            Field currentField = BingoApi.class.getDeclaredField("current");
            currentField.setAccessible(true);
            return currentField.get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object getFieldValue(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Integer invokeIntGetter(Object target, String methodName) {
        Object value = invokeGetter(target, methodName);
        if (value instanceof Integer intValue) {
            return intValue;
        }
        return null;
    }
}
