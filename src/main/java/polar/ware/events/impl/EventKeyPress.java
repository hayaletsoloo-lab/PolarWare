package polar.ware.events.impl;

import polar.ware.events.Event;

public class EventKeyPress extends Event {
    private final int key;
    private final int scanCode;

    public EventKeyPress(int key, int scanCode) {
        this.key = key;
        this.scanCode = scanCode;
    }

    public int getKey() {
        return key;
    }

    public int getScanCode() {
        return scanCode;
    }
}
