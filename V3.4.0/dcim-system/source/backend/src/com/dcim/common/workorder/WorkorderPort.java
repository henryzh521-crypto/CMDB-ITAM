package com.dcim.common.workorder;

import java.util.List;

public interface WorkorderPort {
    String createSystemWo(String woType, String title, String description, String priority, List<String> relatedAssets, List<Object> details);

    boolean hasOpenWoByTitle(String title);
}
