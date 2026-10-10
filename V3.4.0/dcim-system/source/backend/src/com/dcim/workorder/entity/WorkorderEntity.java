package com.dcim.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dcim.common.JsonListTypeHandler;
import com.dcim.common.JsonbTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@TableName(
    value = "workorder",
    autoResultMap = true
)
public class WorkorderEntity {
    @TableId(
        type = IdType.INPUT
    )
    private String woNo;
    private String woType;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String applicant;
    private String applicantUsername;
    private String handler;
    private LocalDateTime applyTime;
    private LocalDate expectFinish;
    private LocalDateTime finishTime;
    private String relatedAssets;
    private String relatedMaterials;
    private Integer applyQty;
    private String targetLocation;
    private String targetRack;
    private Integer targetUnit;
    private String beforeLocation;
    private String process;
    private String completeNote;
    private String acceptResult;
    private String acceptor;
    private LocalDateTime acceptTime;
    private String acceptNote;
    private Boolean autoDeducted;
    private String replacedAsset;
    private String parentWo;
    private String rootCause;
    private BigDecimal costLabor;
    private BigDecimal costService;
    private BigDecimal costOther;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> details;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> replacements;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> cc;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> attachments;
    private LocalDateTime slaResponseDeadline;
    private LocalDateTime slaResolveDeadline;
    private LocalDateTime firstResponseAt;
    private Integer slaEscalations;
    private LocalDateTime lastEscalationAt;
    private Integer approvalLevel;
    @TableField(
        typeHandler = JsonListTypeHandler.class
    )
    private List<Object> approvalUsers;
    @TableField(
        typeHandler = JsonbTypeHandler.class
    )
    private Map<String, Object> approvalDelegate;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;

    
    public String getWoNo() {
        return this.woNo;
    }

    
    public String getWoType() {
        return this.woType;
    }

    
    public String getTitle() {
        return this.title;
    }

    
    public String getDescription() {
        return this.description;
    }

    
    public String getStatus() {
        return this.status;
    }

    
    public String getPriority() {
        return this.priority;
    }

    
    public String getApplicant() {
        return this.applicant;
    }

    
    public String getApplicantUsername() {
        return this.applicantUsername;
    }

    
    public String getHandler() {
        return this.handler;
    }

    
    public LocalDateTime getApplyTime() {
        return this.applyTime;
    }

    
    public LocalDate getExpectFinish() {
        return this.expectFinish;
    }

    
    public LocalDateTime getFinishTime() {
        return this.finishTime;
    }

    
    public String getRelatedAssets() {
        return this.relatedAssets;
    }

    
    public String getRelatedMaterials() {
        return this.relatedMaterials;
    }

    
    public Integer getApplyQty() {
        return this.applyQty;
    }

    
    public String getTargetLocation() {
        return this.targetLocation;
    }

    
    public String getTargetRack() {
        return this.targetRack;
    }

    
    public Integer getTargetUnit() {
        return this.targetUnit;
    }

    
    public String getBeforeLocation() {
        return this.beforeLocation;
    }

    
    public String getProcess() {
        return this.process;
    }

    
    public String getCompleteNote() {
        return this.completeNote;
    }

    
    public String getAcceptResult() {
        return this.acceptResult;
    }

    
    public String getAcceptor() {
        return this.acceptor;
    }

    
    public LocalDateTime getAcceptTime() {
        return this.acceptTime;
    }

    
    public String getAcceptNote() {
        return this.acceptNote;
    }

    
    public Boolean getAutoDeducted() {
        return this.autoDeducted;
    }

    
    public String getReplacedAsset() {
        return this.replacedAsset;
    }

    
    public String getParentWo() {
        return this.parentWo;
    }

    
    public String getRootCause() {
        return this.rootCause;
    }

    
    public BigDecimal getCostLabor() {
        return this.costLabor;
    }

    
    public BigDecimal getCostService() {
        return this.costService;
    }

    
    public BigDecimal getCostOther() {
        return this.costOther;
    }

    
    public List<Object> getDetails() {
        return this.details;
    }

    
    public List<Object> getReplacements() {
        return this.replacements;
    }

    
    public List<Object> getCc() {
        return this.cc;
    }

    
    public List<Object> getAttachments() {
        return this.attachments;
    }

    
    public LocalDateTime getSlaResponseDeadline() {
        return this.slaResponseDeadline;
    }

    
    public LocalDateTime getSlaResolveDeadline() {
        return this.slaResolveDeadline;
    }

    
    public LocalDateTime getFirstResponseAt() {
        return this.firstResponseAt;
    }

    
    public Integer getSlaEscalations() {
        return this.slaEscalations;
    }

    
    public LocalDateTime getLastEscalationAt() {
        return this.lastEscalationAt;
    }

    
    public Integer getApprovalLevel() {
        return this.approvalLevel;
    }

    
    public List<Object> getApprovalUsers() {
        return this.approvalUsers;
    }

    
    public Map<String, Object> getApprovalDelegate() {
        return this.approvalDelegate;
    }

    
    public Integer getVersion() {
        return this.version;
    }

    
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    
    public LocalDateTime getDeletedAt() {
        return this.deletedAt;
    }

    
    public void setWoNo(final String woNo) {
        this.woNo = woNo;
    }

    
    public void setWoType(final String woType) {
        this.woType = woType;
    }

    
    public void setTitle(final String title) {
        this.title = title;
    }

    
    public void setDescription(final String description) {
        this.description = description;
    }

    
    public void setStatus(final String status) {
        this.status = status;
    }

    
    public void setPriority(final String priority) {
        this.priority = priority;
    }

    
    public void setApplicant(final String applicant) {
        this.applicant = applicant;
    }

    
    public void setApplicantUsername(final String applicantUsername) {
        this.applicantUsername = applicantUsername;
    }

    
    public void setHandler(final String handler) {
        this.handler = handler;
    }

    
    public void setApplyTime(final LocalDateTime applyTime) {
        this.applyTime = applyTime;
    }

    
    public void setExpectFinish(final LocalDate expectFinish) {
        this.expectFinish = expectFinish;
    }

    
    public void setFinishTime(final LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    
    public void setRelatedAssets(final String relatedAssets) {
        this.relatedAssets = relatedAssets;
    }

    
    public void setRelatedMaterials(final String relatedMaterials) {
        this.relatedMaterials = relatedMaterials;
    }

    
    public void setApplyQty(final Integer applyQty) {
        this.applyQty = applyQty;
    }

    
    public void setTargetLocation(final String targetLocation) {
        this.targetLocation = targetLocation;
    }

    
    public void setTargetRack(final String targetRack) {
        this.targetRack = targetRack;
    }

    
    public void setTargetUnit(final Integer targetUnit) {
        this.targetUnit = targetUnit;
    }

    
    public void setBeforeLocation(final String beforeLocation) {
        this.beforeLocation = beforeLocation;
    }

    
    public void setProcess(final String process) {
        this.process = process;
    }

    
    public void setCompleteNote(final String completeNote) {
        this.completeNote = completeNote;
    }

    
    public void setAcceptResult(final String acceptResult) {
        this.acceptResult = acceptResult;
    }

    
    public void setAcceptor(final String acceptor) {
        this.acceptor = acceptor;
    }

    
    public void setAcceptTime(final LocalDateTime acceptTime) {
        this.acceptTime = acceptTime;
    }

    
    public void setAcceptNote(final String acceptNote) {
        this.acceptNote = acceptNote;
    }

    
    public void setAutoDeducted(final Boolean autoDeducted) {
        this.autoDeducted = autoDeducted;
    }

    
    public void setReplacedAsset(final String replacedAsset) {
        this.replacedAsset = replacedAsset;
    }

    
    public void setParentWo(final String parentWo) {
        this.parentWo = parentWo;
    }

    
    public void setRootCause(final String rootCause) {
        this.rootCause = rootCause;
    }

    
    public void setCostLabor(final BigDecimal costLabor) {
        this.costLabor = costLabor;
    }

    
    public void setCostService(final BigDecimal costService) {
        this.costService = costService;
    }

    
    public void setCostOther(final BigDecimal costOther) {
        this.costOther = costOther;
    }

    
    public void setDetails(final List<Object> details) {
        this.details = details;
    }

    
    public void setReplacements(final List<Object> replacements) {
        this.replacements = replacements;
    }

    
    public void setCc(final List<Object> cc) {
        this.cc = cc;
    }

    
    public void setAttachments(final List<Object> attachments) {
        this.attachments = attachments;
    }

    
    public void setSlaResponseDeadline(final LocalDateTime slaResponseDeadline) {
        this.slaResponseDeadline = slaResponseDeadline;
    }

    
    public void setSlaResolveDeadline(final LocalDateTime slaResolveDeadline) {
        this.slaResolveDeadline = slaResolveDeadline;
    }

    
    public void setFirstResponseAt(final LocalDateTime firstResponseAt) {
        this.firstResponseAt = firstResponseAt;
    }

    
    public void setSlaEscalations(final Integer slaEscalations) {
        this.slaEscalations = slaEscalations;
    }

    
    public void setLastEscalationAt(final LocalDateTime lastEscalationAt) {
        this.lastEscalationAt = lastEscalationAt;
    }

    
    public void setApprovalLevel(final Integer approvalLevel) {
        this.approvalLevel = approvalLevel;
    }

    
    public void setApprovalUsers(final List<Object> approvalUsers) {
        this.approvalUsers = approvalUsers;
    }

    
    public void setApprovalDelegate(final Map<String, Object> approvalDelegate) {
        this.approvalDelegate = approvalDelegate;
    }

    
    public void setVersion(final Integer version) {
        this.version = version;
    }

    
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    
    public void setDeletedAt(final LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WorkorderEntity other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$applyQty = this.getApplyQty();
            Object other$applyQty = other.getApplyQty();
            if (this$applyQty == null ? other$applyQty == null : this$applyQty.equals(other$applyQty)) {
                Object this$targetUnit = this.getTargetUnit();
                Object other$targetUnit = other.getTargetUnit();
                if (this$targetUnit == null ? other$targetUnit == null : this$targetUnit.equals(other$targetUnit)) {
                    Object this$autoDeducted = this.getAutoDeducted();
                    Object other$autoDeducted = other.getAutoDeducted();
                    if (this$autoDeducted == null ? other$autoDeducted == null : this$autoDeducted.equals(other$autoDeducted)) {
                        Object this$slaEscalations = this.getSlaEscalations();
                        Object other$slaEscalations = other.getSlaEscalations();
                        if (this$slaEscalations == null ? other$slaEscalations == null : this$slaEscalations.equals(other$slaEscalations)) {
                            Object this$approvalLevel = this.getApprovalLevel();
                            Object other$approvalLevel = other.getApprovalLevel();
                            if (this$approvalLevel == null ? other$approvalLevel == null : this$approvalLevel.equals(other$approvalLevel)) {
                                Object this$version = this.getVersion();
                                Object other$version = other.getVersion();
                                if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                                    Object this$woNo = this.getWoNo();
                                    Object other$woNo = other.getWoNo();
                                    if (this$woNo == null ? other$woNo == null : this$woNo.equals(other$woNo)) {
                                        Object this$woType = this.getWoType();
                                        Object other$woType = other.getWoType();
                                        if (this$woType == null ? other$woType == null : this$woType.equals(other$woType)) {
                                            Object this$title = this.getTitle();
                                            Object other$title = other.getTitle();
                                            if (this$title == null ? other$title == null : this$title.equals(other$title)) {
                                                Object this$description = this.getDescription();
                                                Object other$description = other.getDescription();
                                                if (this$description == null ? other$description == null : this$description.equals(other$description)) {
                                                    Object this$status = this.getStatus();
                                                    Object other$status = other.getStatus();
                                                    if (this$status == null ? other$status == null : this$status.equals(other$status)) {
                                                        Object this$priority = this.getPriority();
                                                        Object other$priority = other.getPriority();
                                                        if (this$priority == null ? other$priority == null : this$priority.equals(other$priority)) {
                                                            Object this$applicant = this.getApplicant();
                                                            Object other$applicant = other.getApplicant();
                                                            if (this$applicant == null ? other$applicant == null : this$applicant.equals(other$applicant)) {
                                                                Object this$applicantUsername = this.getApplicantUsername();
                                                                Object other$applicantUsername = other.getApplicantUsername();
                                                                if (this$applicantUsername == null
                                                                    ? other$applicantUsername == null
                                                                    : this$applicantUsername.equals(other$applicantUsername)) {
                                                                    Object this$handler = this.getHandler();
                                                                    Object other$handler = other.getHandler();
                                                                    if (this$handler == null ? other$handler == null : this$handler.equals(other$handler)) {
                                                                        Object this$applyTime = this.getApplyTime();
                                                                        Object other$applyTime = other.getApplyTime();
                                                                        if (this$applyTime == null
                                                                            ? other$applyTime == null
                                                                            : this$applyTime.equals(other$applyTime)) {
                                                                            Object this$expectFinish = this.getExpectFinish();
                                                                            Object other$expectFinish = other.getExpectFinish();
                                                                            if (this$expectFinish == null
                                                                                ? other$expectFinish == null
                                                                                : this$expectFinish.equals(other$expectFinish)) {
                                                                                Object this$finishTime = this.getFinishTime();
                                                                                Object other$finishTime = other.getFinishTime();
                                                                                if (this$finishTime == null
                                                                                    ? other$finishTime == null
                                                                                    : this$finishTime.equals(other$finishTime)) {
                                                                                    Object this$relatedAssets = this.getRelatedAssets();
                                                                                    Object other$relatedAssets = other.getRelatedAssets();
                                                                                    if (this$relatedAssets == null
                                                                                        ? other$relatedAssets == null
                                                                                        : this$relatedAssets.equals(other$relatedAssets)) {
                                                                                        Object this$relatedMaterials = this.getRelatedMaterials();
                                                                                        Object other$relatedMaterials = other.getRelatedMaterials();
                                                                                        if (this$relatedMaterials == null
                                                                                            ? other$relatedMaterials == null
                                                                                            : this$relatedMaterials.equals(other$relatedMaterials)) {
                                                                                            Object this$targetLocation = this.getTargetLocation();
                                                                                            Object other$targetLocation = other.getTargetLocation();
                                                                                            if (this$targetLocation == null
                                                                                                ? other$targetLocation == null
                                                                                                : this$targetLocation.equals(other$targetLocation)) {
                                                                                                Object this$targetRack = this.getTargetRack();
                                                                                                Object other$targetRack = other.getTargetRack();
                                                                                                if (this$targetRack == null
                                                                                                    ? other$targetRack == null
                                                                                                    : this$targetRack.equals(other$targetRack)) {
                                                                                                    Object this$beforeLocation = this.getBeforeLocation();
                                                                                                    Object other$beforeLocation = other.getBeforeLocation();
                                                                                                    if (this$beforeLocation == null
                                                                                                        ? other$beforeLocation == null
                                                                                                        : this$beforeLocation.equals(other$beforeLocation)) {
                                                                                                        Object this$process = this.getProcess();
                                                                                                        Object other$process = other.getProcess();
                                                                                                        if (this$process == null
                                                                                                            ? other$process == null
                                                                                                            : this$process.equals(other$process)) {
                                                                                                            Object this$completeNote = this.getCompleteNote();
                                                                                                            Object other$completeNote = other.getCompleteNote();
                                                                                                            if (this$completeNote == null
                                                                                                                ? other$completeNote == null
                                                                                                                : this$completeNote.equals(other$completeNote)) {
                                                                                                                Object this$acceptResult = this.getAcceptResult();
                                                                                                                Object other$acceptResult = other.getAcceptResult();
                                                                                                                if (this$acceptResult == null
                                                                                                                    ? other$acceptResult == null
                                                                                                                    : this$acceptResult.equals(
                                                                                                                        other$acceptResult
                                                                                                                    )) {
                                                                                                                    Object this$acceptor = this.getAcceptor();
                                                                                                                    Object other$acceptor = other.getAcceptor();
                                                                                                                    if (this$acceptor == null
                                                                                                                        ? other$acceptor == null
                                                                                                                        : this$acceptor.equals(other$acceptor)) {
                                                                                                                        Object this$acceptTime = this.getAcceptTime();
                                                                                                                        Object other$acceptTime = other.getAcceptTime();
                                                                                                                        if (this$acceptTime == null
                                                                                                                            ? other$acceptTime == null
                                                                                                                            : this$acceptTime.equals(
                                                                                                                                other$acceptTime
                                                                                                                            )) {
                                                                                                                            Object this$acceptNote = this.getAcceptNote();
                                                                                                                            Object other$acceptNote = other.getAcceptNote();
                                                                                                                            if (this$acceptNote == null
                                                                                                                                ? other$acceptNote == null
                                                                                                                                : this$acceptNote.equals(
                                                                                                                                    other$acceptNote
                                                                                                                                )) {
                                                                                                                                Object this$replacedAsset = this.getReplacedAsset();
                                                                                                                                Object other$replacedAsset = other.getReplacedAsset();
                                                                                                                                if (this$replacedAsset == null
                                                                                                                                    ? other$replacedAsset
                                                                                                                                        == null
                                                                                                                                    : this$replacedAsset.equals(
                                                                                                                                        other$replacedAsset
                                                                                                                                    )) {
                                                                                                                                    Object this$parentWo = this.getParentWo();
                                                                                                                                    Object other$parentWo = other.getParentWo();
                                                                                                                                    if (this$parentWo == null
                                                                                                                                        ? other$parentWo
                                                                                                                                            == null
                                                                                                                                        : this$parentWo.equals(
                                                                                                                                            other$parentWo
                                                                                                                                        )) {
                                                                                                                                        Object this$rootCause = this.getRootCause();
                                                                                                                                        Object other$rootCause = other.getRootCause();
                                                                                                                                        if (this$rootCause
                                                                                                                                                == null
                                                                                                                                            ? other$rootCause
                                                                                                                                                == null
                                                                                                                                            : this$rootCause.equals(
                                                                                                                                                other$rootCause
                                                                                                                                            )) {
                                                                                                                                            Object this$costLabor = this.getCostLabor();
                                                                                                                                            Object other$costLabor = other.getCostLabor();
                                                                                                                                            if (this$costLabor
                                                                                                                                                    == null
                                                                                                                                                ? other$costLabor
                                                                                                                                                    == null
                                                                                                                                                : this$costLabor.equals(
                                                                                                                                                    other$costLabor
                                                                                                                                                )) {
                                                                                                                                                Object this$costService = this.getCostService();
                                                                                                                                                Object other$costService = other.getCostService();
                                                                                                                                                if (this$costService
                                                                                                                                                        == null
                                                                                                                                                    ? other$costService
                                                                                                                                                        == null
                                                                                                                                                    : this$costService.equals(
                                                                                                                                                        other$costService
                                                                                                                                                    )) {
                                                                                                                                                    Object this$costOther = this.getCostOther();
                                                                                                                                                    Object other$costOther = other.getCostOther();
                                                                                                                                                    if (this$costOther
                                                                                                                                                            == null
                                                                                                                                                        ? other$costOther
                                                                                                                                                            == null
                                                                                                                                                        : this$costOther.equals(
                                                                                                                                                            other$costOther
                                                                                                                                                        )) {
                                                                                                                                                        Object this$details = this.getDetails();
                                                                                                                                                        Object other$details = other.getDetails();
                                                                                                                                                        if (this$details
                                                                                                                                                                == null
                                                                                                                                                            ? other$details
                                                                                                                                                                == null
                                                                                                                                                            : this$details.equals(
                                                                                                                                                                other$details
                                                                                                                                                            )) {
                                                                                                                                                            Object this$replacements = this.getReplacements();
                                                                                                                                                            Object other$replacements = other.getReplacements();
                                                                                                                                                            if (this$replacements
                                                                                                                                                                    == null
                                                                                                                                                                ? other$replacements
                                                                                                                                                                    == null
                                                                                                                                                                : this$replacements.equals(
                                                                                                                                                                    other$replacements
                                                                                                                                                                )
                                                                                                                                                                )
                                                                                                                                                             {
                                                                                                                                                                Object this$cc = this.getCc();
                                                                                                                                                                Object other$cc = other.getCc();
                                                                                                                                                                if (this$cc
                                                                                                                                                                        == null
                                                                                                                                                                    ? other$cc
                                                                                                                                                                        == null
                                                                                                                                                                    : this$cc.equals(
                                                                                                                                                                        other$cc
                                                                                                                                                                    )
                                                                                                                                                                    )
                                                                                                                                                                 {
                                                                                                                                                                    Object this$attachments = this.getAttachments();
                                                                                                                                                                    Object other$attachments = other.getAttachments();
                                                                                                                                                                    if (this$attachments
                                                                                                                                                                            == null
                                                                                                                                                                        ? other$attachments
                                                                                                                                                                            == null
                                                                                                                                                                        : this$attachments.equals(
                                                                                                                                                                            other$attachments
                                                                                                                                                                        )
                                                                                                                                                                        )
                                                                                                                                                                     {
                                                                                                                                                                        Object this$slaResponseDeadline = this.getSlaResponseDeadline();
                                                                                                                                                                        Object other$slaResponseDeadline = other.getSlaResponseDeadline();
                                                                                                                                                                        if (this$slaResponseDeadline
                                                                                                                                                                                == null
                                                                                                                                                                            ? other$slaResponseDeadline
                                                                                                                                                                                == null
                                                                                                                                                                            : this$slaResponseDeadline.equals(
                                                                                                                                                                                other$slaResponseDeadline
                                                                                                                                                                            )
                                                                                                                                                                            )
                                                                                                                                                                         {
                                                                                                                                                                            Object this$slaResolveDeadline = this.getSlaResolveDeadline();
                                                                                                                                                                            Object other$slaResolveDeadline = other.getSlaResolveDeadline();
                                                                                                                                                                            if (this$slaResolveDeadline
                                                                                                                                                                                    == null
                                                                                                                                                                                ? other$slaResolveDeadline
                                                                                                                                                                                    == null
                                                                                                                                                                                : this$slaResolveDeadline.equals(
                                                                                                                                                                                    other$slaResolveDeadline
                                                                                                                                                                                )
                                                                                                                                                                                )
                                                                                                                                                                             {
                                                                                                                                                                                Object this$firstResponseAt = this.getFirstResponseAt();
                                                                                                                                                                                Object other$firstResponseAt = other.getFirstResponseAt();
                                                                                                                                                                                if (this$firstResponseAt
                                                                                                                                                                                        == null
                                                                                                                                                                                    ? other$firstResponseAt
                                                                                                                                                                                        == null
                                                                                                                                                                                    : this$firstResponseAt.equals(
                                                                                                                                                                                        other$firstResponseAt
                                                                                                                                                                                    )
                                                                                                                                                                                    )
                                                                                                                                                                                 {
                                                                                                                                                                                    Object this$lastEscalationAt = this.getLastEscalationAt();
                                                                                                                                                                                    Object other$lastEscalationAt = other.getLastEscalationAt();
                                                                                                                                                                                    if (this$lastEscalationAt
                                                                                                                                                                                            == null
                                                                                                                                                                                        ? other$lastEscalationAt
                                                                                                                                                                                            == null
                                                                                                                                                                                        : this$lastEscalationAt.equals(
                                                                                                                                                                                            other$lastEscalationAt
                                                                                                                                                                                        )
                                                                                                                                                                                        )
                                                                                                                                                                                     {
                                                                                                                                                                                        Object this$approvalUsers = this.getApprovalUsers();
                                                                                                                                                                                        Object other$approvalUsers = other.getApprovalUsers();
                                                                                                                                                                                        if (this$approvalUsers
                                                                                                                                                                                                == null
                                                                                                                                                                                            ? other$approvalUsers
                                                                                                                                                                                                == null
                                                                                                                                                                                            : this$approvalUsers.equals(
                                                                                                                                                                                                other$approvalUsers
                                                                                                                                                                                            )
                                                                                                                                                                                            )
                                                                                                                                                                                         {
                                                                                                                                                                                            Object this$approvalDelegate = this.getApprovalDelegate();
                                                                                                                                                                                            Object other$approvalDelegate = other.getApprovalDelegate();
                                                                                                                                                                                            if (this$approvalDelegate
                                                                                                                                                                                                    == null
                                                                                                                                                                                                ? other$approvalDelegate
                                                                                                                                                                                                    == null
                                                                                                                                                                                                : this$approvalDelegate.equals(
                                                                                                                                                                                                    other$approvalDelegate
                                                                                                                                                                                                )
                                                                                                                                                                                                )
                                                                                                                                                                                             {
                                                                                                                                                                                                Object this$createdAt = this.getCreatedAt();
                                                                                                                                                                                                Object other$createdAt = other.getCreatedAt();
                                                                                                                                                                                                if (this$createdAt
                                                                                                                                                                                                        == null
                                                                                                                                                                                                    ? other$createdAt
                                                                                                                                                                                                        == null
                                                                                                                                                                                                    : this$createdAt.equals(
                                                                                                                                                                                                        other$createdAt
                                                                                                                                                                                                    )
                                                                                                                                                                                                    )
                                                                                                                                                                                                 {
                                                                                                                                                                                                    Object this$updatedAt = this.getUpdatedAt();
                                                                                                                                                                                                    Object other$updatedAt = other.getUpdatedAt();
                                                                                                                                                                                                    if (this$updatedAt
                                                                                                                                                                                                            == null
                                                                                                                                                                                                        ? other$updatedAt
                                                                                                                                                                                                            == null
                                                                                                                                                                                                        : this$updatedAt.equals(
                                                                                                                                                                                                            other$updatedAt
                                                                                                                                                                                                        )
                                                                                                                                                                                                        )
                                                                                                                                                                                                     {
                                                                                                                                                                                                        Object this$deletedAt = this.getDeletedAt();
                                                                                                                                                                                                        Object other$deletedAt = other.getDeletedAt();
                                                                                                                                                                                                        return this$deletedAt
                                                                                                                                                                                                                == null
                                                                                                                                                                                                            ? other$deletedAt
                                                                                                                                                                                                                == null
                                                                                                                                                                                                            : this$deletedAt.equals(
                                                                                                                                                                                                                other$deletedAt
                                                                                                                                                                                                            );
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
        return other instanceof WorkorderEntity;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $applyQty = this.getApplyQty();
        result = result * 59 + ($applyQty == null ? 43 : $applyQty.hashCode());
        Object $targetUnit = this.getTargetUnit();
        result = result * 59 + ($targetUnit == null ? 43 : $targetUnit.hashCode());
        Object $autoDeducted = this.getAutoDeducted();
        result = result * 59 + ($autoDeducted == null ? 43 : $autoDeducted.hashCode());
        Object $slaEscalations = this.getSlaEscalations();
        result = result * 59 + ($slaEscalations == null ? 43 : $slaEscalations.hashCode());
        Object $approvalLevel = this.getApprovalLevel();
        result = result * 59 + ($approvalLevel == null ? 43 : $approvalLevel.hashCode());
        Object $version = this.getVersion();
        result = result * 59 + ($version == null ? 43 : $version.hashCode());
        Object $woNo = this.getWoNo();
        result = result * 59 + ($woNo == null ? 43 : $woNo.hashCode());
        Object $woType = this.getWoType();
        result = result * 59 + ($woType == null ? 43 : $woType.hashCode());
        Object $title = this.getTitle();
        result = result * 59 + ($title == null ? 43 : $title.hashCode());
        Object $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        Object $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        Object $priority = this.getPriority();
        result = result * 59 + ($priority == null ? 43 : $priority.hashCode());
        Object $applicant = this.getApplicant();
        result = result * 59 + ($applicant == null ? 43 : $applicant.hashCode());
        Object $applicantUsername = this.getApplicantUsername();
        result = result * 59 + ($applicantUsername == null ? 43 : $applicantUsername.hashCode());
        Object $handler = this.getHandler();
        result = result * 59 + ($handler == null ? 43 : $handler.hashCode());
        Object $applyTime = this.getApplyTime();
        result = result * 59 + ($applyTime == null ? 43 : $applyTime.hashCode());
        Object $expectFinish = this.getExpectFinish();
        result = result * 59 + ($expectFinish == null ? 43 : $expectFinish.hashCode());
        Object $finishTime = this.getFinishTime();
        result = result * 59 + ($finishTime == null ? 43 : $finishTime.hashCode());
        Object $relatedAssets = this.getRelatedAssets();
        result = result * 59 + ($relatedAssets == null ? 43 : $relatedAssets.hashCode());
        Object $relatedMaterials = this.getRelatedMaterials();
        result = result * 59 + ($relatedMaterials == null ? 43 : $relatedMaterials.hashCode());
        Object $targetLocation = this.getTargetLocation();
        result = result * 59 + ($targetLocation == null ? 43 : $targetLocation.hashCode());
        Object $targetRack = this.getTargetRack();
        result = result * 59 + ($targetRack == null ? 43 : $targetRack.hashCode());
        Object $beforeLocation = this.getBeforeLocation();
        result = result * 59 + ($beforeLocation == null ? 43 : $beforeLocation.hashCode());
        Object $process = this.getProcess();
        result = result * 59 + ($process == null ? 43 : $process.hashCode());
        Object $completeNote = this.getCompleteNote();
        result = result * 59 + ($completeNote == null ? 43 : $completeNote.hashCode());
        Object $acceptResult = this.getAcceptResult();
        result = result * 59 + ($acceptResult == null ? 43 : $acceptResult.hashCode());
        Object $acceptor = this.getAcceptor();
        result = result * 59 + ($acceptor == null ? 43 : $acceptor.hashCode());
        Object $acceptTime = this.getAcceptTime();
        result = result * 59 + ($acceptTime == null ? 43 : $acceptTime.hashCode());
        Object $acceptNote = this.getAcceptNote();
        result = result * 59 + ($acceptNote == null ? 43 : $acceptNote.hashCode());
        Object $replacedAsset = this.getReplacedAsset();
        result = result * 59 + ($replacedAsset == null ? 43 : $replacedAsset.hashCode());
        Object $parentWo = this.getParentWo();
        result = result * 59 + ($parentWo == null ? 43 : $parentWo.hashCode());
        Object $rootCause = this.getRootCause();
        result = result * 59 + ($rootCause == null ? 43 : $rootCause.hashCode());
        Object $costLabor = this.getCostLabor();
        result = result * 59 + ($costLabor == null ? 43 : $costLabor.hashCode());
        Object $costService = this.getCostService();
        result = result * 59 + ($costService == null ? 43 : $costService.hashCode());
        Object $costOther = this.getCostOther();
        result = result * 59 + ($costOther == null ? 43 : $costOther.hashCode());
        Object $details = this.getDetails();
        result = result * 59 + ($details == null ? 43 : $details.hashCode());
        Object $replacements = this.getReplacements();
        result = result * 59 + ($replacements == null ? 43 : $replacements.hashCode());
        Object $cc = this.getCc();
        result = result * 59 + ($cc == null ? 43 : $cc.hashCode());
        Object $attachments = this.getAttachments();
        result = result * 59 + ($attachments == null ? 43 : $attachments.hashCode());
        Object $slaResponseDeadline = this.getSlaResponseDeadline();
        result = result * 59 + ($slaResponseDeadline == null ? 43 : $slaResponseDeadline.hashCode());
        Object $slaResolveDeadline = this.getSlaResolveDeadline();
        result = result * 59 + ($slaResolveDeadline == null ? 43 : $slaResolveDeadline.hashCode());
        Object $firstResponseAt = this.getFirstResponseAt();
        result = result * 59 + ($firstResponseAt == null ? 43 : $firstResponseAt.hashCode());
        Object $lastEscalationAt = this.getLastEscalationAt();
        result = result * 59 + ($lastEscalationAt == null ? 43 : $lastEscalationAt.hashCode());
        Object $approvalUsers = this.getApprovalUsers();
        result = result * 59 + ($approvalUsers == null ? 43 : $approvalUsers.hashCode());
        Object $approvalDelegate = this.getApprovalDelegate();
        result = result * 59 + ($approvalDelegate == null ? 43 : $approvalDelegate.hashCode());
        Object $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
        Object $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : $updatedAt.hashCode());
        Object $deletedAt = this.getDeletedAt();
        return result * 59 + ($deletedAt == null ? 43 : $deletedAt.hashCode());
    }

    
    @Override
    public String toString() {
        return "WorkorderEntity(woNo="
            + this.getWoNo()
            + ", woType="
            + this.getWoType()
            + ", title="
            + this.getTitle()
            + ", description="
            + this.getDescription()
            + ", status="
            + this.getStatus()
            + ", priority="
            + this.getPriority()
            + ", applicant="
            + this.getApplicant()
            + ", applicantUsername="
            + this.getApplicantUsername()
            + ", handler="
            + this.getHandler()
            + ", applyTime="
            + this.getApplyTime()
            + ", expectFinish="
            + this.getExpectFinish()
            + ", finishTime="
            + this.getFinishTime()
            + ", relatedAssets="
            + this.getRelatedAssets()
            + ", relatedMaterials="
            + this.getRelatedMaterials()
            + ", applyQty="
            + this.getApplyQty()
            + ", targetLocation="
            + this.getTargetLocation()
            + ", targetRack="
            + this.getTargetRack()
            + ", targetUnit="
            + this.getTargetUnit()
            + ", beforeLocation="
            + this.getBeforeLocation()
            + ", process="
            + this.getProcess()
            + ", completeNote="
            + this.getCompleteNote()
            + ", acceptResult="
            + this.getAcceptResult()
            + ", acceptor="
            + this.getAcceptor()
            + ", acceptTime="
            + this.getAcceptTime()
            + ", acceptNote="
            + this.getAcceptNote()
            + ", autoDeducted="
            + this.getAutoDeducted()
            + ", replacedAsset="
            + this.getReplacedAsset()
            + ", parentWo="
            + this.getParentWo()
            + ", rootCause="
            + this.getRootCause()
            + ", costLabor="
            + this.getCostLabor()
            + ", costService="
            + this.getCostService()
            + ", costOther="
            + this.getCostOther()
            + ", details="
            + this.getDetails()
            + ", replacements="
            + this.getReplacements()
            + ", cc="
            + this.getCc()
            + ", attachments="
            + this.getAttachments()
            + ", slaResponseDeadline="
            + this.getSlaResponseDeadline()
            + ", slaResolveDeadline="
            + this.getSlaResolveDeadline()
            + ", firstResponseAt="
            + this.getFirstResponseAt()
            + ", slaEscalations="
            + this.getSlaEscalations()
            + ", lastEscalationAt="
            + this.getLastEscalationAt()
            + ", approvalLevel="
            + this.getApprovalLevel()
            + ", approvalUsers="
            + this.getApprovalUsers()
            + ", approvalDelegate="
            + this.getApprovalDelegate()
            + ", version="
            + this.getVersion()
            + ", createdAt="
            + this.getCreatedAt()
            + ", updatedAt="
            + this.getUpdatedAt()
            + ", deletedAt="
            + this.getDeletedAt()
            + ")";
    }
}
