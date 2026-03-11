package com.todo.dto;

public class TodoUpdateRequest {
    private String title;
    private String content;
    private Boolean completed;
    private String startDate;
    private String endDate;
    private String priority;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}
