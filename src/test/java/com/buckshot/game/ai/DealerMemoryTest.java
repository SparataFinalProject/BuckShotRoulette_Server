package com.buckshot.game.ai;

import com.buckshot.game.Shell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DealerMemoryTest {

    private final DealerMemory memory = new DealerMemory();

    @Test
    @DisplayName("장전하면 개수를 기억하고, 돋보기 기억은 없다")
    void remembersLoadedCounts(){
        memory.onLoaded(2,3);

        assertEquals(2, memory.liveCount());
        assertEquals(3, memory.blankCount());
        assertNull(memory.knownShell());
    }

    @Test
    @DisplayName("실탄 1, 공포탄 2가 빠지면 그만큼 준다")
    void decreasesRemovedShells(){
        memory.onLoaded(2,3);
        memory.onShellRemoved(Shell.LIVE);
        memory.onShellRemoved(Shell.BLANK);
        memory.onShellRemoved(Shell.BLANK);

        assertEquals(1,memory.liveCount());
        assertEquals(1,memory.blankCount());
    }

    @Test
    @DisplayName("돋보기로 본 탄은 그 탄이 빠지면 잊는다")
    void forgetsPeekedShellAfterRemoval(){
        memory.onLoaded(2,3);
        memory.onPeeked(Shell.LIVE);

        assertEquals(Shell.LIVE,memory.knownShell());

        memory.onShellRemoved(Shell.LIVE);

        assertNull(memory.knownShell());
    }

    @Test
    @DisplayName("다시 장전하면 돋보기로 본 것도 잊는다")
    void reloadForgetsPeekedShell(){
        memory.onLoaded(1,0);
        memory.onPeeked(Shell.LIVE);

        memory.onLoaded(2,0);

        assertNull(memory.knownShell());
    }

    @Test
    @DisplayName("개수가 0인데 그 종류가 빠져도 음수가 안 된다")
    void neverGoesNegative(){
        memory.onLoaded(1,0);
        memory.onShellRemoved(Shell.BLANK);

        assertEquals(1, memory.liveCount());
        assertEquals(0,memory.blankCount());
    }
}
