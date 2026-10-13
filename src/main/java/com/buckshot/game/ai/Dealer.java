package com.buckshot.game.ai;

import com.buckshot.game.GamePlayer;
import com.buckshot.game.Item;
import com.buckshot.game.Shell;
import com.buckshot.game.SlotItem;

public class Dealer {

    private final DealerBrain brain;
    private final DealerMemory memory;

    public Dealer(DealerBrain brain) {
        this.brain = brain;
        memory = new DealerMemory();
    }

    public void onLoaded(int live,int blank){
        memory.onLoaded(live, blank);
    }

    public void onShellRemoved(Shell shell){
        memory.onShellRemoved(shell);
    }

    public void onPeeked(Shell shell){
        memory.onPeeked(shell);
    }

    public DealerAction decide(GamePlayer me, GamePlayer opponent, boolean sawActive){
        return brain.decide(memory.observe(me,opponent,sawActive));
    }

    public int slotOf(GamePlayer me, Item item) {
        for (SlotItem slot : me.items()) {
            if (slot.item() == item) {
                return slot.slot();
            }
        }
        throw new IllegalStateException("딜러에게 " + item + " 이(가) 없음");
    }
}
