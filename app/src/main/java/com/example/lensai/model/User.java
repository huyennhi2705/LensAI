package com.example.lensai.model;

// User.java
public class User {
    public String uid;
    public String email;
    public String fullName;
    public long createdAt;

    // Hàm khởi tạo không tham số bắt buộc cho Firebase
    public User() {
    }

    public User(String uid, String email, String fullName) {
        this.uid = uid;
        this.email = email;
        this.fullName = fullName;
        this.createdAt = System.currentTimeMillis();
    }

    // Getter và Setter (tùy chọn, Firebase có thể truy cập trực tiếp vào trường public)
    public String getUid() { return uid; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public long getCreatedAt() { return createdAt; }
}