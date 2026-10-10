package com.dcim.common.asset;

import java.util.Map;

public interface AssetOpsPort {
    String bizStatusOf(String assetNo);

    void transitBiz(String assetNo, String target, String reason, String refWo);

    void placeAsset(String assetNo, String rackCode, Integer unitNo, Integer uHeight, String refWo);

    void unplaceAsset(String assetNo, String refWo);

    void transferAsset(String assetNo, String dstRoom, String dstRack, Integer dstUnit, String dstRegion, String refWo);

    void transferAssetToRoom(String assetNo, String dstRoom, String dstRegion, String refWo);

    String createAssetForRfid(
        String name, String sn, String categoryCode, String modelCode, String ownerType, String regionCode, Map<String, Object> attrs, String refDoc
    );

    void enrichFromDiscovery(String assetNo, String mgmtIp, Map<String, Object> hwAttrs, String refDoc);

    void replaceComponent(String parentAssetNo, String oldAssetNo, String newAssetNo, String refWo, String oldStatus);

    void recordEvent(String assetNo, String eventType, String refDoc, Map<String, Object> snapshot);
}
