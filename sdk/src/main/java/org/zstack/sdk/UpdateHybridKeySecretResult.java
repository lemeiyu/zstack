package org.zstack.sdk;

import org.zstack.sdk.HybridAccountInventory;

public class UpdateHybridKeySecretResult {
    public HybridAccountInventory inventory;
    public void setInventory(HybridAccountInventory inventory) {
        this.inventory = inventory;
    }
    public HybridAccountInventory getInventory() {
        return this.inventory;
    }

}
