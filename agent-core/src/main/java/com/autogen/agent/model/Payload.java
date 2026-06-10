package com.autogen.agent.model;

public class Payload {
    private String format;
    private String encoding;
    private Object body;
    private boolean truncated;

    public Payload() {
    }

    public Payload(String format, String encoding, Object body, boolean truncated) {
        this.format = format;
        this.encoding = encoding;
        this.body = body;
        this.truncated = truncated;
    }

    public static Payload text(String format, Object body, boolean truncated) {
        return new Payload(format, "plain", body, truncated);
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}
