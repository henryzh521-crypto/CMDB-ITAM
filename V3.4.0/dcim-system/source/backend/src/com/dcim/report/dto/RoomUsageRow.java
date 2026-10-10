package com.dcim.report.dto;


public class RoomUsageRow {
    private String key;
    private String label;
    private Long uCap;
    private Long uOcc;

    
    public String getKey() {
        return this.key;
    }

    
    public String getLabel() {
        return this.label;
    }

    
    public Long getUCap() {
        return this.uCap;
    }

    
    public Long getUOcc() {
        return this.uOcc;
    }

    
    public void setKey(final String key) {
        this.key = key;
    }

    
    public void setLabel(final String label) {
        this.label = label;
    }

    
    public void setUCap(final Long uCap) {
        this.uCap = uCap;
    }

    
    public void setUOcc(final Long uOcc) {
        this.uOcc = uOcc;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof RoomUsageRow other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else {
            Object this$uCap = this.getUCap();
            Object other$uCap = other.getUCap();
            if (this$uCap == null ? other$uCap == null : this$uCap.equals(other$uCap)) {
                Object this$uOcc = this.getUOcc();
                Object other$uOcc = other.getUOcc();
                if (this$uOcc == null ? other$uOcc == null : this$uOcc.equals(other$uOcc)) {
                    Object this$key = this.getKey();
                    Object other$key = other.getKey();
                    if (this$key == null ? other$key == null : this$key.equals(other$key)) {
                        Object this$label = this.getLabel();
                        Object other$label = other.getLabel();
                        return this$label == null ? other$label == null : this$label.equals(other$label);
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
        return other instanceof RoomUsageRow;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Object $uCap = this.getUCap();
        result = result * 59 + ($uCap == null ? 43 : $uCap.hashCode());
        Object $uOcc = this.getUOcc();
        result = result * 59 + ($uOcc == null ? 43 : $uOcc.hashCode());
        Object $key = this.getKey();
        result = result * 59 + ($key == null ? 43 : $key.hashCode());
        Object $label = this.getLabel();
        return result * 59 + ($label == null ? 43 : $label.hashCode());
    }

    
    @Override
    public String toString() {
        return "RoomUsageRow(key=" + this.getKey() + ", label=" + this.getLabel() + ", uCap=" + this.getUCap() + ", uOcc=" + this.getUOcc() + ")";
    }
}
