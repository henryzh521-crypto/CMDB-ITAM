package com.dcim.common.rack;

public interface RackOccupancyPort {
    void syncAssetPlacement(String assetNo, String oldRack, Integer oldUnit, Integer oldUHeight, String newRack, Integer newUnit, Integer newUHeight);
}
