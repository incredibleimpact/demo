package com.example.demo.exception;

public class CacheDegradeException extends RuntimeException {
    public CacheDegradeException(String message) {
        super(message);
    }
}
