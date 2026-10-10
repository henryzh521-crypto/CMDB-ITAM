package com.dcim.workorder.controller;

import com.dcim.common.ApiResult;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.workorder.service.WorkorderAttachmentService;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/workorders"})
public class WorkorderAttachmentController {
    private final WorkorderAttachmentService attachmentService;

    @PostMapping({"/{woNo}/attachments"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPLOAD_ATTACHMENT",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Map<String, Object>> upload(@PathVariable String woNo, @RequestParam("file") MultipartFile file) {
        return ApiResult.ok(this.attachmentService.upload(woNo, file));
    }

    @GetMapping({"/{woNo}/attachments"})
    @RequirePerm(
        module = "workorders"
    )
    public ApiResult<List<Map<String, Object>>> list(@PathVariable String woNo) {
        return ApiResult.ok(this.attachmentService.list(woNo));
    }

    @GetMapping({"/{woNo}/attachments/{id}/download"})
    @RequirePerm(
        module = "workorders"
    )
    public ResponseEntity<Resource> download(@PathVariable String woNo, @PathVariable Long id) {
        return this.attachmentService.download(woNo, id);
    }

    @DeleteMapping({"/{woNo}/attachments/{id}"})
    @RequirePerm(
        module = "workorders",
        write = true,
        action = "update"
    )
    @Audit(
        action = "DELETE_ATTACHMENT",
        module = "workorders",
        objType = "workorder",
        objIdSpEL = "#woNo"
    )
    public ApiResult<Void> delete(@PathVariable String woNo, @PathVariable Long id) {
        this.attachmentService.delete(woNo, id);
        return ApiResult.ok();
    }

    
    public WorkorderAttachmentController(final WorkorderAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }
}
