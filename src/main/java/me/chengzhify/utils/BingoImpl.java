package me.chengzhify.utils;

import me.jfenn.bingo.api.BingoApi;
import me.jfenn.bingo.api.IBingoApi;
import me.jfenn.bingo.api.data.IBingoGame;
import me.jfenn.bingo.api.data.BingoGameStatus;
import me.jfenn.bingo.api.data.IBingoTeam;

import java.lang.reflect.Field;
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

    private static String getInternalStateName() {
        Object api = getCurrentApi();
        if (api == null) {
            return null;
        }
        // The public STARTING status also covers world loading; only prompt in COUNTDOWN.
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
}
