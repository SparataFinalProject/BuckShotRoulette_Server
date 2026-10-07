package com.buckshot.game.ai;

import com.buckshot.game.Shell;

/**
 * 딜러가 지금까지 본 것으로 약실 상태를 기억한다.
 * 사람 플레이어가 패킷으로 아는 정보(장전 개수, 빠진 탄, 내 돋보기 결과)만 받는다.
 */
public class DealerMemory {

    private int liveCount;
    private int blankCount;
    private Shell knownShell;

    public int liveCount() {
        return liveCount;
    }

    public int blankCount() {
        return blankCount;
    }

    public Shell knownShell() {
        return knownShell;
    }

    public void onLoaded(int live, int blank) {
        liveCount = live;
        blankCount = blank;
        knownShell = null;
    }

    public void onShellRemoved(Shell shell) {
        if (shell == Shell.LIVE && liveCount > 0) {
            liveCount--;
        } else if (shell == Shell.BLANK && blankCount > 0) {
            blankCount--;
        }

        knownShell = null;
    }

    public void onPeeked(Shell shell) {
        knownShell = shell;
    }
}
