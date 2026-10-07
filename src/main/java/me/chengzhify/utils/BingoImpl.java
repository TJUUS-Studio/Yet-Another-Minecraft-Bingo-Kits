package me.chengzhify.utils;

import me.jfenn.bingo.api.BingoApi;
import me.jfenn.bingo.api.IBingoApi;
import me.jfenn.bingo.api.data.IBingoGame;
import me.jfenn.bingo.api.data.BingoGameStatus;
import me.jfenn.bingo.api.data.IBingoTeam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class BingoImpl {

    public static boolean isAvailable() {
        IBingoApi api = getCurrentApi();
        return api != null && api.getGame() != null && api.getTeams() != null;
    }

    public static boolean isInTeam(UUID uuid) {
        if (!isAvailable()) return false;

        for (IBingoTeam team : getCurrentApi().getTeams()) {
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

        for (IBingoTeam team : getCurrentApi().getTeams()) {
            for (UUID members : team.getPlayers()) {
                if (members.equals(uuid)) {
                    return team.getId();
                }
            }
        }
        return null;
    }

    public static boolean isStarted() {
        IBingoApi api = getCurrentApi();
        IBingoGame game = api == null ? null : api.getGame();
        if (game != null) {
            return game.getStatus().equals(BingoGameStatus.PLAYING);
        }
        return false;
    }

    public static boolean isStarting() {
        IBingoApi api = getCurrentApi();
        IBingoGame game = api == null ? null : api.getGame();
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
        // Public STARTING also covers loading; the selection menu needs the COUNTDOWN phase.
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

    private static IBingoApi getCurrentApi() {
        return BingoApi.getINSTANCE();
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
