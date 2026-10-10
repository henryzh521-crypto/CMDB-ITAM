package com.dcim.common;


public class ApiResult<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(0, "ok", null);
    }

    public static <T> ApiResult<T> error(int code, String msg) {
        return new ApiResult<>(code, msg, null);
    }

    
    public int getCode() {
        return this.code;
    }

    
    public String getMsg() {
        return this.msg;
    }

    
    public T getData() {
        return this.data;
    }

    
    public void setCode(final int code) {
        this.code = code;
    }

    
    public void setMsg(final String msg) {
        this.msg = msg;
    }

    
    public void setData(final T data) {
        this.data = data;
    }

    
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ApiResult<?> other)) {
            return false;
        } else if (!other.canEqual(this)) {
            return false;
        } else if (this.getCode() != other.getCode()) {
            return false;
        } else {
            Object this$msg = this.getMsg();
            Object other$msg = other.getMsg();
            if (this$msg == null ? other$msg == null : this$msg.equals(other$msg)) {
                Object this$data = this.getData();
                Object other$data = other.getData();
                return this$data == null ? other$data == null : this$data.equals(other$data);
            } else {
                return false;
            }
        }
    }

    
    protected boolean canEqual(final Object other) {
        return other instanceof ApiResult;
    }

    
    @Override
    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        result = result * 59 + this.getCode();
        Object $msg = this.getMsg();
        result = result * 59 + ($msg == null ? 43 : $msg.hashCode());
        Object $data = this.getData();
        return result * 59 + ($data == null ? 43 : $data.hashCode());
    }

    
    @Override
    public String toString() {
        return "ApiResult(code=" + this.getCode() + ", msg=" + this.getMsg() + ", data=" + this.getData() + ")";
    }

    
    public ApiResult(final int code, final String msg, final T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }
}
