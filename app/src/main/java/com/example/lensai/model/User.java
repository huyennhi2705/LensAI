package com.example.lensai.model;
public class User {
    public String uid;
    public String email;
    public String fullName;
    public long createdAt;
    public User() {
    }

    public User(String uid, String email, String fullName) {
        this.uid = uid;
        this.email = email;
        this.fullName = fullName;
        this.createdAt = System.currentTimeMillis();
    }
    public String getUid() { return uid; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public long getCreatedAt() { return createdAt; }
}