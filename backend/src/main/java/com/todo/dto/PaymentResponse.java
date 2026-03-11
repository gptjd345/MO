package com.todo.dto;

public class PaymentResponse {
    private Long id;
    private String status;
    private String pgTransactionId;
    private String failReason;
    private Long amount;

    public PaymentResponse() {}

    public PaymentResponse(Long id, String status, String pgTransactionId, String failReason, Long amount) {
        this.id = id;
        this.status = status;
        this.pgTransactionId = pgTransactionId;
        this.failReason = failReason;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPgTransactionId() { return pgTransactionId; }
    public void setPgTransactionId(String pgTransactionId) { this.pgTransactionId = pgTransactionId; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
}
