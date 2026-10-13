package com.buckshot.streak.timeline;

import com.buckshot.ws.packet.ItemType;
import com.buckshot.ws.packet.ShellType;
import java.util.ArrayList;
import java.util.List;

public class TimelineRecorder {

    public static final int VERSION = 1;

    private final List<Timeline.Round> rounds = new ArrayList<>();
    private List<Timeline.Turn> turns;
    private List<Timeline.Act> acts;
    private Timeline.End end;

    public void roundLoaded(boolean meFirst, List<String> shells) {
        StringBuilder order = new StringBuilder(shells.size());
        for (String shell : shells) order.append(bit(shell));
        turns = new ArrayList<>();
        acts = null;
        rounds.add(new Timeline.Round(rounds.size() + 1, who(meFirst), order.toString(), turns));
    }

    public void turnStarted(int n, boolean me, boolean cuffSkip) {
        if (turns == null) return;   // 장전 전 (생기지 않지만 방어)
        acts = new ArrayList<>();
        turns.add(new Timeline.Turn(n, who(me), cuffSkip ? Boolean.TRUE : null, acts));
    }

    public void fired(boolean self, String shell, int damage) {
        add(new Timeline.Act(Timeline.Act.FIRE, self ? Timeline.Act.SELF : Timeline.Act.OPP, bit(shell), damage, null));
    }

    public void itemUsed(String item, String revealedShell, String ejectedShell) {
        Timeline.Act act = switch (item) {
            case ItemType.MAGNIFIER -> new Timeline.Act(Timeline.Act.MAG, null, null, null,
                    revealedShell == null ? null : bit(revealedShell));
            case ItemType.BEER -> new Timeline.Act(Timeline.Act.BEER, null,
                    ejectedShell == null || ejectedShell.isEmpty() ? null : bit(ejectedShell), null, null);
            case ItemType.HANDCUFFS -> simple(Timeline.Act.CUFF);
            case ItemType.CIGARETTE -> simple(Timeline.Act.CIG);
            case ItemType.SAW -> simple(Timeline.Act.SAW);
            default -> simple(item);
        };
        add(act);
    }

    public void ended(String reason, boolean meWon) {
        if (end == null) end = new Timeline.End(reason, who(meWon));
    }

    public Timeline build() {
        return new Timeline(VERSION, rounds, end);
    }

    private void add(Timeline.Act act) {
        if (acts != null) acts.add(act);
    }

    private static Timeline.Act simple(String kind) {
        return new Timeline.Act(kind, null, null, null, null);
    }

    private static String who(boolean me) {
        return me ? Timeline.ME : Timeline.DEALER;
    }

    private static int bit(String shell) {
        return ShellType.LIVE.equals(shell) ? 1 : 0;
    }
}