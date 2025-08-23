package com.inf1nlty.shop.client.state;

/**
 * Client-side ephemeral state for shop UIs.
 */
public class ShopClientData {

    /** Player balance in tenths (one decimal currency). */
    public static int balance = 0;

    /** True only while the system shop GUI is open (enables system price tooltip injection). */
    public static boolean inShop = false;

    /** True only while the global shop GUI is open (enables global listing tooltip injection). */
    public static boolean inGlobalShop = false;
}