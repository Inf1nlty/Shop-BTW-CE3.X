package com.inf1nlty.shop.util;

/**
 * Currency formatting utilities (tenths).
 */
public final class Money {

    private Money() {}

    public static String format(int tenths) {
        int whole = tenths / 10;
        int frac = Math.abs(tenths % 10);
        return whole + "." + frac;
    }
}