package com.example.demo.utils.lock;

public interface ILock {
    boolean tryLock(long timeoutSec);
    void delLock();
}
