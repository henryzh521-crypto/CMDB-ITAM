package com.dcim.collector.dto;

import java.util.List;
import java.util.Map;

public final class CollectorDtos {
    private CollectorDtos() {
    }

    public static record ClaimReq(String name, String categoryCode, String modelCode, String regionCode) {
    }

    public static record CreateCredentialReq(String credName, String username, String password) {
    }

    public static record CreateTaskReq(String name, String cidr, String protocol, Long credentialId, String regionCode) {
    }

    public static record EndpointImportRow(String bmcIp, String assetNo) {
    }

    public static record FoundDevice(
        String bmcIp, String sn, String manufacturer, String model, String protocol, String redfishVersion, String ipmiVersion, Map<String, Object> raw
    ) {
    }

    public static record GpuCardDto(Integer slot, String gpuSn, String model, Integer memoryGb, String fw, String batchNo, Boolean migCapable) {
    }

    public static record GpuMetricDto(
        String cardNo, String hostAssetNo, String ts, Double tempC, Double powerW, Double utilPct, Integer eccDbe, Integer xidErrors
    ) {
    }

    public static record PushMetricsReq(List<CollectorDtos.GpuMetricDto> metrics) {
    }

    public static record PushResultsReq(Integer scannedIps, Boolean done, Boolean ok, String error, List<CollectorDtos.FoundDevice> found) {
    }

    public static record PushSnapshotReq(
        String bmcIp, String protocol, Boolean ok, String error, Map<String, Object> snapshot, List<CollectorDtos.GpuCardDto> gpuCards
    ) {
    }
}
