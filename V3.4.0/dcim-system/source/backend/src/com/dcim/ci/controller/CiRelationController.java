package com.dcim.ci.controller;

import com.dcim.ci.service.CiGraphService;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/ci-relations"})
public class CiRelationController {
    private final JdbcTemplate jdbc;
    private final CiGraphService graph;

    @GetMapping({"/meta"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<Map<String, Object>> meta() {
        List<Map<String, String>> rels = new ArrayList<>();
        CiGraphService.REL_TYPES.forEach((k, v) -> rels.add(Map.of("value", k, "label", v)));
        List<Map<String, String>> types = List.of(
            Map.of("value", "ASSET", "label", "资产"), Map.of("value", "RACK", "label", "机柜"), Map.of("value", "ROOM", "label", "机房")
        );
        return ApiResult.ok(Map.of("relTypes", rels, "ciTypes", types));
    }

    @GetMapping
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<List<Map<String, Object>>> list(@RequestParam String srcType, @RequestParam String srcId) {
        return ApiResult.ok(this.graph.relationsOf(srcType.trim().toUpperCase(), srcId.trim()));
    }

    @PostMapping
    @RequirePerm(
        module = "assets",
        write = true,
        action = "update"
    )
    @Audit(
        action = "CREATE",
        module = "assets",
        objType = "ci_relation",
        objIdSpEL = "#req.srcId() + '->' + #req.dstId()"
    )
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid CiRelationController.CreateReq req) {
        String srcType = req.srcType().trim().toUpperCase();
        String dstType = req.dstType().trim().toUpperCase();
        String rel = req.relType().trim().toUpperCase();
        String srcId = req.srcId().trim();
        String dstId = req.dstId().trim();
        this.graph.validateRel(rel);
        this.graph.requireCi(srcType, srcId);
        this.graph.requireCi(dstType, dstId);
        if (srcType.equals(dstType) && srcId.equals(dstId)) {
            throw BusinessException.badRequest("不能与自身建立关系");
        } else {
            LoginUser user = CurrentUser.get();

            Long id;
            try {
                id = (Long)this.jdbc
                    .queryForObject(
                        "INSERT INTO ci_relation (src_type, src_id, rel_type, dst_type, dst_id, created_by, remark) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                        Long.class,
                        new Object[]{srcType, srcId, rel, dstType, dstId, user == null ? null : user.username(), blankToNull(req.remark())}
                    );
            } catch (DuplicateKeyException var10) {
                throw BusinessException.conflict("该关系已存在");
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            return ApiResult.ok(out);
        }
    }

    @DeleteMapping({"/{id}"})
    @RequirePerm(
        module = "assets",
        write = true,
        action = "update"
    )
    @Audit(
        action = "DELETE",
        module = "assets",
        objType = "ci_relation",
        objIdSpEL = "#id"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable Long id) {
        int n = this.jdbc.update("UPDATE ci_relation SET valid_to = NOW() WHERE id = ? AND valid_to IS NULL", new Object[]{id});
        if (n == 0) {
            throw BusinessException.notFound("关系不存在或已解除");
        } else {
            return ApiResult.ok();
        }
    }

    @GetMapping({"/impact"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<Map<String, Object>> impact(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String id,
        @RequestParam(required = false) String assetNo,
        @RequestParam(defaultValue = "downstream") String direction,
        @RequestParam(defaultValue = "6") int depth
    ) {
        String t = type != null && !type.isBlank() ? type.trim().toUpperCase() : "ASSET";
        String i = id != null && !id.isBlank() ? id : assetNo;
        if (i != null && !i.isBlank()) {
            CiGraphService.Graph g = this.graph.traverse(t, i.trim(), !"upstream".equalsIgnoreCase(direction), depth);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("root", g.root());
            out.put("nodes", g.nodes());
            out.put("edges", g.edges());
            out.put("truncated", g.truncated());
            out.put("summary", summary(g));
            return ApiResult.ok(out);
        } else {
            throw BusinessException.badRequest("请指定配置项");
        }
    }

    /** v3.5：全量拓扑（网络拓扑页；roomCode 可选，过滤单机房） */
    @GetMapping({"/graph"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<Map<String, Object>> graph(@RequestParam(required = false) String roomCode) {
        CiGraphService.FullGraph g = this.graph.fullGraph(roomCode);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", g.nodes());
        out.put("edges", g.edges());
        out.put("truncated", g.truncated());
        out.put("summary", g.summary());
        return ApiResult.ok(out);
    }

    private static Map<String, Long> summary(CiGraphService.Graph g) {
        Map<String, Long> m = new LinkedHashMap<>();

        for (CiGraphService.Node n : g.nodes()) {
            if (n.depth() > 0) {
                m.merge(n.type(), 1L, Long::sum);
            }
        }

        return m;
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    
    public CiRelationController(final JdbcTemplate jdbc, final CiGraphService graph) {
        this.jdbc = jdbc;
        this.graph = graph;
    }

    public static record CreateReq(
        @NotBlank String srcType,
        @NotBlank @Size(max = 50) String srcId,
        @NotBlank String relType,
        @NotBlank String dstType,
        @NotBlank @Size(max = 50) String dstId,
        @Size(max = 200) String remark
    ) {
    }
}
