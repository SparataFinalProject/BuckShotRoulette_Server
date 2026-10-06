package com.buckshot.game;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 한 라운드 동안 장전된 탄. 맨 앞이 약실이고, 순서는 서버 밖으로 나가지 않는다.
 */
public class Magazine {

    private final Deque<Shell> shells;
    private final int liveCount;
    private final int blankCount;

    public Magazine(List<Shell> order) {
        this.shells = new ArrayDeque<>(order);
        this.liveCount = (int) order.stream().filter(s -> s == Shell.LIVE).count();
        this.blankCount = order.size() - liveCount;
    }

    /** 약실 탄 확인 (돋보기). 탄을 빼지 않는다. */
    public Shell peek() {
        requireNotEmpty();
        return shells.peekFirst();
    }

    /** 약실 탄을 꺼낸다 (발사·맥주). */
    public Shell pop() {
        requireNotEmpty();
        return shells.pollFirst();
    }

    public int remaining() {
        return shells.size();
    }

    public boolean isEmpty() {
        return shells.isEmpty();
    }

    /** 장전 시점 실탄 수 (ROUND_START로 공개되는 값). */
    public int liveCount() {
        return liveCount;
    }

    /** 장전 시점 공포탄 수. */
    public int blankCount() {
        return blankCount;
    }

    private void requireNotEmpty() {
        if (shells.isEmpty()) {
            throw new IllegalStateException("magazine is empty");
        }
    }
}
