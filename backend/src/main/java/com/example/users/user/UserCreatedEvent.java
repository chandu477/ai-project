package com.example.users.user;

/** Published after a new user has been committed. */
public record UserCreatedEvent(Long userId) {
}
