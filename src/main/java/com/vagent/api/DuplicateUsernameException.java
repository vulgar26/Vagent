package com.vagent.api;

/**
 * 注册时用户名已存在，映射 HTTP 409 Conflict。
 */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("用户名已存在: " + username);
    }
}
