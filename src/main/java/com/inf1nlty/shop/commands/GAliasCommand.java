package com.inf1nlty.shop.commands;

/**
 * Alias for /gshop command, supports all subcommands.
 * /g [sell|s|buy|b|my|m|unlist|u|mailbox|mb] ...
 */
public class GAliasCommand extends GlobalShopCommand {
    @Override
    public String getCommandName() {
        return "gs";
    }
}