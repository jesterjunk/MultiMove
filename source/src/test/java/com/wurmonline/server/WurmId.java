package com.wurmonline.server;
public final class WurmId {
    public static int getType(long id) { return (int)(id & 255); }
}
