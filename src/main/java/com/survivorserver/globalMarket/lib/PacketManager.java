package com.survivorserver.globalMarket.lib;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.survivorserver.globalMarket.Market;

public class PacketManager {
	
    Market market;
    SignInput input;
    BossMessage message;

    public PacketManager(Market market) {
        this.market = market;
        input = new SignInput(market);
        message = new BossMessage(market);
    }

    public SignInput getSignInput() {
        return input;
    }

    public BossMessage getMessage() {
        return message;
    }

    public void unregister() {
        ProtocolManager man = ProtocolLibrary.getProtocolManager();
        if (man != null) {
            man.removePacketListeners(market);
        }
    }
}
