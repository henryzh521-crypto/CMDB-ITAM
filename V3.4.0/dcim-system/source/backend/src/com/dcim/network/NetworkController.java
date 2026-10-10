package com.dcim.network;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/network"})
public class NetworkController {
    private final IpamService ipam;
    private final CableService cables;

    @GetMapping({"/prefixes"})
    @RequirePerm(
        module = "network"
    )
    public ApiResult<List<Map<String, Object>>> prefixes(
        @RequestParam(required = false) String keyword, @RequestParam(required = false) String roomCode, @RequestParam(required = false) String purpose
    ) {
        return ApiResult.ok(this.ipam.prefixes(keyword, roomCode, purpose));
    }

    @PostMapping({"/prefixes"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "network",
        objType = "ipam_prefix",
        objIdSpEL = "#req.prefix()"
    )
    public ApiResult<Map<String, Object>> createPrefix(@RequestBody IpamService.PrefixReq req) {
        return ApiResult.ok(Map.of("id", this.ipam.savePrefix(null, req, user())));
    }

    @PutMapping({"/prefixes/{id}"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "network",
        objType = "ipam_prefix",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> updatePrefix(@PathVariable Long id, @RequestBody IpamService.PrefixReq req) {
        return ApiResult.ok(Map.of("id", this.ipam.savePrefix(id, req, user())));
    }

    @DeleteMapping({"/prefixes/{id}"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "network",
        objType = "ipam_prefix",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> deletePrefix(@PathVariable Long id) {
        this.ipam.deletePrefix(id);
        return ApiResult.ok();
    }

    @GetMapping({"/prefixes/{id}/addresses"})
    @RequirePerm(
        module = "network"
    )
    public ApiResult<Map<String, Object>> addresses(
        @PathVariable Long id,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "50") long size
    ) {
        return ApiResult.ok(this.ipam.addresses(id, status, page, size));
    }

    @GetMapping({"/prefixes/{id}/next-free"})
    @RequirePerm(
        module = "network"
    )
    public ApiResult<List<String>> nextFree(@PathVariable Long id, @RequestParam(defaultValue = "1") int count) {
        return ApiResult.ok(this.ipam.nextFree(id, count));
    }

    @PostMapping({"/addresses"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "create"
    )
    @Audit(
        action = "ALLOCATE",
        module = "network",
        objType = "ipam_address",
        objIdSpEL = "#req.ip()"
    )
    public ApiResult<Map<String, Object>> allocate(@RequestBody IpamService.AddressReq req) {
        return ApiResult.ok(this.ipam.allocate(req, user()));
    }

    @PutMapping({"/addresses/{id}"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "network",
        objType = "ipam_address",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> updateAddress(@PathVariable Long id, @RequestBody IpamService.AddressReq req) {
        this.ipam.updateAddress(id, req);
        return ApiResult.ok();
    }

    @DeleteMapping({"/addresses/{id}"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "RELEASE",
        module = "network",
        objType = "ipam_address",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> release(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean clearAsset) {
        this.ipam.release(id, clearAsset);
        return ApiResult.ok();
    }

    @GetMapping({"/lookup"})
    @RequirePerm(
        module = "network"
    )
    public ApiResult<Map<String, Object>> lookup(@RequestParam String ip) {
        return ApiResult.ok(this.ipam.lookup(ip));
    }

    @GetMapping({"/conflicts"})
    @RequirePerm(
        module = "network"
    )
    public ApiResult<Map<String, Object>> conflicts() {
        return ApiResult.ok(this.ipam.conflicts());
    }

    @PostMapping({"/sync"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "update"
    )
    @Audit(
        action = "SYNC",
        module = "network",
        objType = "ipam_address"
    )
    public ApiResult<Map<String, Integer>> sync() {
        return ApiResult.ok(this.ipam.syncAll());
    }

    @GetMapping({"/vlans"})
    @RequirePerm(
        module = "network"
    )
    public ApiResult<List<Map<String, Object>>> vlans(@RequestParam(required = false) String roomCode) {
        return ApiResult.ok(this.ipam.vlans(roomCode));
    }

    @PostMapping({"/vlans"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "network",
        objType = "ipam_vlan",
        objIdSpEL = "#req.vlanId()"
    )
    public ApiResult<Void> createVlan(@RequestBody IpamService.VlanReq req) {
        this.ipam.saveVlan(null, req);
        return ApiResult.ok();
    }

    @PutMapping({"/vlans/{id}"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "network",
        objType = "ipam_vlan",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> updateVlan(@PathVariable Long id, @RequestBody IpamService.VlanReq req) {
        this.ipam.saveVlan(id, req);
        return ApiResult.ok();
    }

    @DeleteMapping({"/vlans/{id}"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "network",
        objType = "ipam_vlan",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> deleteVlan(@PathVariable Long id) {
        this.ipam.deleteVlan(id);
        return ApiResult.ok();
    }

    @GetMapping({"/cables/meta"})
    @RequirePerm(
        module = "network"
    )
    public ApiResult<Map<String, Object>> cableMeta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("types", CableService.TYPES);
        m.put("statuses", Map.of("PLANNED", "规划", "CONNECTED", "已连接", "REMOVED", "已拆除"));
        return ApiResult.ok(m);
    }

    @GetMapping({"/cables"})
    @RequirePerm(
        module = "network"
    )
    public ApiResult<Map<String, Object>> cables(
        @RequestParam(required = false) String assetNo,
        @RequestParam(required = false) String rackCode,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResult.ok(this.cables.list(assetNo, rackCode, type, status, keyword, page, size));
    }

    @GetMapping({"/cables/ports/{assetNo}"})
    @RequirePerm(
        module = "network"
    )
    public ApiResult<List<Map<String, Object>>> ports(@PathVariable String assetNo) {
        return ApiResult.ok(this.cables.ports(assetNo));
    }

    @PostMapping({"/cables"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "network",
        objType = "cable",
        objIdSpEL = "#req.cableNo()"
    )
    public ApiResult<Map<String, Object>> createCable(@RequestBody CableService.CableReq req) {
        return ApiResult.ok(this.cables.create(req, user()));
    }

    @PutMapping({"/cables/{id}"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "network",
        objType = "cable",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> updateCable(@PathVariable Long id, @RequestBody CableService.CableReq req) {
        return ApiResult.ok(this.cables.update(id, req, user()));
    }

    @PostMapping({"/cables/{id}/remove"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "update"
    )
    @Audit(
        action = "REMOVE",
        module = "network",
        objType = "cable",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> removeCable(@PathVariable Long id) {
        this.cables.remove(id, user());
        return ApiResult.ok();
    }

    @DeleteMapping({"/cables/{id}"})
    @RequirePerm(
        module = "network",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "network",
        objType = "cable",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> deleteCable(@PathVariable Long id) {
        this.cables.delete(id, user());
        return ApiResult.ok();
    }

    private static String user() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else {
            return u.username();
        }
    }

    
    public NetworkController(final IpamService ipam, final CableService cables) {
        this.ipam = ipam;
        this.cables = cables;
    }
}
