package com.dcim.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(
    prefix = "wecom"
)
public class WecomProperties {
    private boolean enabled = false;
    private String corpId;
    private String agentId;
    private String secret;

    
    public boolean isEnabled() {
        return this.enabled;
    }

    
    public String getCorpId() {
        return this.corpId;
    }

    
    public String getAgentId() {
        return this.agentId;
    }

    
    public String getSecret() {
        return this.secret;
    }

    
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    
    public void setCorpId(final String corpId) {
        this.corpId = corpId;
    }

    
    public void setAgentId(final String agentId) {
        this.agentId = agentId;
    }

    
    public void setSecret(final String secret) {
        this.secret = secret;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof WecomProperties other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else if (this.isEnabled() != other.isEnabled()) {
            return false;
        } else {
            Object this$corpId = this.getCorpId();
            Object other$corpId = other.getCorpId();
            if (this$corpId == null ? other$corpId == null : this$corpId.equals(other$corpId)) {
                Object this$agentId = this.getAgentId();
                Object other$agentId = other.getAgentId();
                if (this$agentId == null ? other$agentId == null : this$agentId.equals(other$agentId)) {
                    Object this$secret = this.getSecret();
                    Object other$secret = other.getSecret();
                    return this$secret == null ? other$secret == null : this$secret.equals(other$secret);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof WecomProperties;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * 59 + (this.isEnabled() ? 79 : 97);
        Object $corpId = this.getCorpId();
        result = result * 59 + ($corpId == null ? 43 : $corpId.hashCode());
        Object $agentId = this.getAgentId();
        result = result * 59 + ($agentId == null ? 43 : $agentId.hashCode());
        Object $secret = this.getSecret();
        return result * 59 + ($secret == null ? 43 : $secret.hashCode());
    }

    
    @Override
    public String toString() {
        return "WecomProperties(enabled="
            + this.isEnabled()
            + ", corpId="
            + this.getCorpId()
            + ", agentId="
            + this.getAgentId()
            + ", secret="
            + this.getSecret()
            + ")";
    }
}
