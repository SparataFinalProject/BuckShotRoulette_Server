package com.buckshot.game.rule;

import com.buckshot.game.GameRules;
import com.buckshot.game.Shell;
import com.buckshot.game.ShotTarget;

/**
 * 발사 판정.
 */
public final class FireRules {

    private FireRules() {
    }

    /** 실탄 1 (톱 적용 시 2), 공포탄 0. */
    public static int damage(Shell shell, boolean sawActive) {
        if (shell == Shell.BLANK) {
            return 0;
        }
        return sawActive ? GameRules.LIVE_DAMAGE * GameRules.SAW_MULTIPLIER : GameRules.LIVE_DAMAGE;
    }

    /** 자기 자신에게 쏴서 공포탄일 때만 턴 유지. */
    public static boolean keepsTurn(ShotTarget target, Shell shell) {
        return target == ShotTarget.SELF && shell == Shell.BLANK;
    }
}
