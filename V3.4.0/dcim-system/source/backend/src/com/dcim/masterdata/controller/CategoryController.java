package com.dcim.masterdata.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.csv.CsvSupport;
import com.dcim.masterdata.entity.CategoryEntity;
import com.dcim.masterdata.mapper.CategoryMapper;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/categories"})
public class CategoryController {
    private static final int MAX_LEVEL = 5;
    private final CategoryMapper categoryMapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate rowTx;
    private static final List<String> CSV_HEADERS = List.of("分类编码", "分类名称", "父分类编码", "排序", "备注");

    @org.springframework.beans.factory.annotation.Autowired
    private com.dcim.security.RbacService rbacForTree;

    /**
     * v3.4：分类树是资产、物料页面显示分类名所需的参考数据，能读分类 / 资产 / 物料任一模块即可读取
     * （v3.3 只认分类模块：只授予资产权限的角色打开资产页会报「没有执行该操作的权限」）。
     */
    @GetMapping({"/tree"})
    public ApiResult<List<Map<String, Object>>> tree() {
        com.dcim.security.LoginUser u = com.dcim.security.CurrentUser.get();
        if (!this.rbacForTree.canRead(u, "categories") && !this.rbacForTree.canRead(u, "assets") && !this.rbacForTree.canRead(u, "materials")) {
            throw new org.springframework.security.access.AccessDeniedException("没有执行该操作的权限");
        }
        List<CategoryEntity> all = this.categoryMapper.selectList(null);
        Map<String, List<CategoryEntity>> byParent = all.stream().collect(Collectors.groupingBy(c -> c.getParentCode() == null ? "" : c.getParentCode()));
        List<Map<String, Object>> roots = byParent.getOrDefault("", List.of())
            .stream()
            .sorted(
                Comparator.comparing(CategoryEntity::getSort, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CategoryEntity::getCategoryCode)
            )
            .map(c -> this.toNode(c, byParent))
            .collect(Collectors.toList());
        return ApiResult.ok(roots);
    }

    @PostMapping
    @RequirePerm(
        module = "categories",
        write = true
    )
    @Audit(
        action = "CREATE",
        module = "categories",
        objType = "category",
        objIdSpEL = "#req.categoryCode"
    )
    @Transactional
    public ApiResult<Void> create(@RequestBody @Valid CategoryController.CreateCategoryReq req) {
        String code = req.getCategoryCode().trim();
        if (code.isEmpty()) {
            throw BusinessException.badRequest("分类编码不能为空");
        } else if (this.selectByCode(code) != null) {
            throw BusinessException.conflict("分类编码已存在");
        } else {
            CategoryEntity c = new CategoryEntity();
            c.setCategoryCode(code);
            c.setName(req.getName().trim());
            c.setSort(req.getSort() == null ? 0 : req.getSort());
            c.setStatus(1);
            c.setRemark(req.getRemark());
            if (req.getParentCode() != null && !req.getParentCode().isBlank()) {
                CategoryEntity parent = this.selectByCode(req.getParentCode().trim());
                if (parent == null) {
                    throw BusinessException.badRequest("父分类不存在");
                }

                int level = parent.getLevel() + 1;
                if (level > 5) {
                    throw BusinessException.badRequest("分类层级不能超过 5 级");
                }

                c.setParentCode(parent.getCategoryCode());
                c.setLevel(level);
                c.setPath(parent.getPath() + code + "/");
            } else {
                c.setLevel(1);
                c.setPath("/" + code + "/");
            }

            c.setCreatedAt(LocalDateTime.now());
            this.categoryMapper.insert(c);
            return ApiResult.ok();
        }
    }

    @PutMapping({"/{categoryCode}"})
    @RequirePerm(
        module = "categories",
        write = true
    )
    @Audit(
        action = "UPDATE",
        module = "categories",
        objType = "category",
        objIdSpEL = "#categoryCode"
    )
    @Transactional
    public ApiResult<Void> update(@PathVariable String categoryCode, @RequestBody CategoryController.UpdateCategoryReq req) {
        CategoryEntity c = this.selectByCode(categoryCode);
        if (c == null) {
            throw BusinessException.notFound("分类不存在");
        } else {
            if (req.getName() != null) {
                if (req.getName().isBlank()) {
                    throw BusinessException.badRequest("分类名称不能为空");
                }

                c.setName(req.getName().trim());
            }

            if (req.getSort() != null) {
                c.setSort(req.getSort());
            }

            if (req.getStatus() != null) {
                c.setStatus(req.getStatus());
            }

            if (req.getRemark() != null) {
                c.setRemark(req.getRemark());
            }

            this.categoryMapper.updateById(c);
            return ApiResult.ok();
        }
    }

    @DeleteMapping({"/{categoryCode}"})
    @RequirePerm(
        module = "categories",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "categories",
        objType = "category",
        objIdSpEL = "#categoryCode"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable String categoryCode) {
        CategoryEntity c = this.selectByCode(categoryCode);
        if (c == null) {
            throw BusinessException.notFound("分类不存在");
        } else {
            Long children = this.categoryMapper.selectCount(Wrappers.<CategoryEntity>lambdaQuery().eq(CategoryEntity::getParentCode, c.getCategoryCode()));
            if (children != null && children > 0L) {
                throw BusinessException.conflict("存在子分类，不允许删除");
            } else {
                Long assetRefs = this.referenceCountOf("asset", "category_code", c.getCategoryCode());
                if (assetRefs != null && assetRefs > 0L) {
                    throw BusinessException.conflict("该分类已被资产引用，不允许删除");
                } else {
                    this.categoryMapper.deleteById(c.getId());
                    return ApiResult.ok();
                }
            }
        }
    }

    @GetMapping({"/template"})
    @RequirePerm(
        module = "categories"
    )
    public void template(HttpServletResponse response) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff').append(CsvSupport.line(CSV_HEADERS)).append('\n');
        csv.append(CsvSupport.line(List.of("SRV", "服务器", "", "10", "一级分类示例"))).append('\n');
        csv.append(CsvSupport.line(List.of("SRV-GPU", "GPU服务器", "SRV", "10", "子级需父编码已存在"))).append('\n');
        this.writeCsv(response, "分类导入模板.csv", csv.toString());
    }

    @PostMapping(
        value = {"/import"},
        consumes = {"multipart/form-data"}
    )
    @RequirePerm(
        module = "categories",
        write = true,
        action = "import"
    )
    @Audit(
        action = "IMPORT",
        module = "categories",
        objType = "category"
    )
    public ApiResult<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file != null && !file.isEmpty()) {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<List<String>> rows = CsvSupport.parse(text);
            if (rows.isEmpty()) {
                throw BusinessException.badRequest("CSV 文件为空");
            } else {
                List<String> header = rows.get(0);
                String firstCell = header.isEmpty() ? "" : header.get(0).trim();
                boolean hasHeader = "分类编码".equals(firstCell);
                Map<String, Integer> colIndex = new LinkedHashMap<>();
                if (hasHeader) {
                    for (int i = 0; i < header.size(); i++) {
                        colIndex.put(header.get(i).trim(), i);
                    }
                }

                List<List<String>> dataRows = rows.subList(hasHeader ? 1 : 0, rows.size());
                Set<String> fileCodes = new HashSet<>();
                List<int[]> pendingIdx = new ArrayList<>();

                for (int i = 0; i < dataRows.size(); i++) {
                    pendingIdx.add(new int[]{i});
                }

                Map<Integer, Map<String, Object>> errors = new LinkedHashMap<>();
                int success = 0;

                for (int round = 0; round <= 5 && !pendingIdx.isEmpty(); round++) {
                    List<int[]> still = new ArrayList<>();

                    for (int[] wrap : pendingIdx) {
                        int i = wrap[0];
                        int rowNo = (hasHeader ? 1 : 0) + i + 1;

                        try {
                            this.importCategoryRow(dataRows.get(i), colIndex, fileCodes);
                            success++;
                        } catch (BusinessException var23) {
                            String reason = var23.getMessage();
                            boolean parentMissing = reason != null && reason.contains("父分类不存在");
                            if (parentMissing && round < 5) {
                                still.add(wrap);
                            } else {
                                Map<String, Object> err = new LinkedHashMap<>();
                                err.put("row", rowNo);
                                err.put("reason", reason);
                                errors.put(rowNo, err);
                            }
                        } catch (DataIntegrityViolationException var24) {
                            Map<String, Object> err = new LinkedHashMap<>();
                            err.put("row", rowNo);
                            err.put("reason", "数据写入冲突（可能已被并发导入）");
                            errors.put(rowNo, err);
                        }
                    }

                    pendingIdx = still;
                }

                for (int[] wrap : pendingIdx) {
                    int rowNo = (hasHeader ? 1 : 0) + wrap[0] + 1;
                    if (!errors.containsKey(rowNo)) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("row", rowNo);
                        err.put("reason", "父分类不存在（请确认父编码已在库中或排在本行之前）");
                        errors.put(rowNo, err);
                    }
                }

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("total", dataRows.size());
                out.put("success", success);
                out.put("failed", errors.size());
                out.put("errors", new ArrayList<>(errors.values()));
                return ApiResult.ok(out);
            }
        } else {
            throw BusinessException.badRequest("请上传要导入的 CSV 文件");
        }
    }

    private void importCategoryRow(List<String> cells, Map<String, Integer> colIndex, Set<String> fileCodes) {
        int[] ix = new int[]{col(colIndex, "分类编码", 0), col(colIndex, "分类名称", 1), col(colIndex, "父分类编码", 2), col(colIndex, "排序", 3), col(colIndex, "备注", 4)};
        String code = blankToNull(cellAt(cells, ix[0]));
        String name = blankToNull(cellAt(cells, ix[1]));
        String parentCode = blankToNull(cellAt(cells, ix[2]));
        String sort = blankToNull(cellAt(cells, ix[3]));
        String remark = blankToNull(cellAt(cells, ix[4]));
        if (code == null) {
            throw BusinessException.badRequest("分类编码不能为空");
        } else if (name == null) {
            throw BusinessException.badRequest("分类名称不能为空");
        } else if (this.selectByCode(code) != null) {
            throw BusinessException.conflict("分类编码已存在：" + code);
        } else if (fileCodes.contains(code)) {
            throw BusinessException.conflict("分类编码已存在（文件内重复）：" + code);
        } else {
            CategoryEntity c = new CategoryEntity();
            c.setCategoryCode(code);
            c.setName(name);
            c.setStatus(1);
            c.setSort(parseSort(sort));
            c.setRemark(remark);
            if (parentCode != null) {
                CategoryEntity parent = this.selectByCode(parentCode);
                if (parent == null) {
                    throw BusinessException.badRequest("父分类不存在：" + parentCode);
                }

                int level = parent.getLevel() + 1;
                if (level > 5) {
                    throw BusinessException.badRequest("分类层级不能超过 5 级");
                }

                c.setParentCode(parent.getCategoryCode());
                c.setLevel(level);
                c.setPath(parent.getPath() + code + "/");
            } else {
                c.setLevel(1);
                c.setPath("/" + code + "/");
            }

            c.setCreatedAt(LocalDateTime.now());

            try {
                this.rowTx.executeWithoutResult(tx -> this.categoryMapper.insert(c));
            } catch (DuplicateKeyException var13) {
                throw BusinessException.conflict("分类编码已存在：" + code);
            }

            fileCodes.add(code);
        }
    }

    private static int parseSort(String s) {
        if (s != null && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException var2) {
                throw BusinessException.badRequest("排序必须是整数：" + s);
            }
        } else {
            return 0;
        }
    }

    private static int col(Map<String, Integer> colIndex, String name, int fallback) {
        Integer i = colIndex.get(name);
        return i == null ? fallback : i;
    }

    private static String cellAt(List<String> cells, int idx) {
        return idx >= 0 && idx < cells.size() ? cells.get(idx) : null;
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private void writeCsv(HttpServletResponse response, String fileName, String csv) throws IOException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setStatus(200);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    private CategoryEntity selectByCode(String code) {
        return (CategoryEntity)this.categoryMapper.selectOne(Wrappers.<CategoryEntity>lambdaQuery().eq(CategoryEntity::getCategoryCode, code));
    }

    private Long referenceCountOf(String table, String column, String code) {
        Long n = (Long)this.jdbc
            .queryForObject("SELECT COUNT(*) FROM " + table + " WHERE deleted_at IS NULL AND " + column + " = ?", Long.class, new Object[]{code});
        return n == null ? 0L : n;
    }

    private Map<String, Object> toNode(CategoryEntity c, Map<String, List<CategoryEntity>> byParent) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("categoryCode", c.getCategoryCode());
        node.put("name", c.getName());
        node.put("parentCode", c.getParentCode());
        node.put("level", c.getLevel());
        node.put("path", c.getPath());
        node.put("sort", c.getSort());
        node.put("status", c.getStatus());
        node.put("remark", c.getRemark());
        List<Map<String, Object>> children = byParent.getOrDefault(c.getCategoryCode(), List.of())
            .stream()
            .sorted(
                Comparator.comparing(CategoryEntity::getSort, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CategoryEntity::getCategoryCode)
            )
            .map(ch -> this.toNode(ch, byParent))
            .collect(Collectors.toList());
        node.put("children", children);
        return node;
    }

    
    public CategoryController(final CategoryMapper categoryMapper, final JdbcTemplate jdbc, final TransactionTemplate rowTx) {
        this.categoryMapper = categoryMapper;
        this.jdbc = jdbc;
        this.rowTx = rowTx;
    }

    public static class CreateCategoryReq {
        @NotBlank(
            message = "分类编码不能为空"
        )
        @Size(
            max = 50,
            message = "分类编码最长 50 字符"
        )
        private String categoryCode;
        @NotBlank(
            message = "分类名称不能为空"
        )
        @Size(
            max = 100,
            message = "分类名称最长 100 字符"
        )
        private String name;
        private String parentCode;
        private Integer sort;
        private String remark;

        
        public String getCategoryCode() {
            return this.categoryCode;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getParentCode() {
            return this.parentCode;
        }

        
        public Integer getSort() {
            return this.sort;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setCategoryCode(final String categoryCode) {
            this.categoryCode = categoryCode;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setParentCode(final String parentCode) {
            this.parentCode = parentCode;
        }

        
        public void setSort(final Integer sort) {
            this.sort = sort;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof CategoryController.CreateCategoryReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$sort = this.getSort();
                Object other$sort = other.getSort();
                if (this$sort == null ? other$sort == null : this$sort.equals(other$sort)) {
                    Object this$categoryCode = this.getCategoryCode();
                    Object other$categoryCode = other.getCategoryCode();
                    if (this$categoryCode == null ? other$categoryCode == null : this$categoryCode.equals(other$categoryCode)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$parentCode = this.getParentCode();
                            Object other$parentCode = other.getParentCode();
                            if (this$parentCode == null ? other$parentCode == null : this$parentCode.equals(other$parentCode)) {
                                Object this$remark = this.getRemark();
                                Object other$remark = other.getRemark();
                                return this$remark == null ? other$remark == null : this$remark.equals(other$remark);
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof CategoryController.CreateCategoryReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $sort = this.getSort();
            result = result * 59 + ($sort == null ? 43 : $sort.hashCode());
            Object $categoryCode = this.getCategoryCode();
            result = result * 59 + ($categoryCode == null ? 43 : $categoryCode.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $parentCode = this.getParentCode();
            result = result * 59 + ($parentCode == null ? 43 : $parentCode.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "CategoryController.CreateCategoryReq(categoryCode="
                + this.getCategoryCode()
                + ", name="
                + this.getName()
                + ", parentCode="
                + this.getParentCode()
                + ", sort="
                + this.getSort()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class UpdateCategoryReq {
        private String name;
        private Integer sort;
        private Integer status;
        private String remark;

        
        public String getName() {
            return this.name;
        }

        
        public Integer getSort() {
            return this.sort;
        }

        
        public Integer getStatus() {
            return this.status;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setSort(final Integer sort) {
            this.sort = sort;
        }

        
        public void setStatus(final Integer status) {
            this.status = status;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof CategoryController.UpdateCategoryReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$sort = this.getSort();
                Object other$sort = other.getSort();
                if (this$sort == null ? other$sort == null : this$sort.equals(other$sort)) {
                    Object this$status = this.getStatus();
                    Object other$status = other.getStatus();
                    if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$remark = this.getRemark();
                            Object other$remark = other.getRemark();
                            return this$remark == null ? other$remark == null : this$remark.equals(other$remark);
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof CategoryController.UpdateCategoryReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $sort = this.getSort();
            result = result * 59 + ($sort == null ? 43 : $sort.hashCode());
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "CategoryController.UpdateCategoryReq(name="
                + this.getName()
                + ", sort="
                + this.getSort()
                + ", status="
                + this.getStatus()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }
}
