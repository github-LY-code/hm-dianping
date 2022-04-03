package com.hmdp.service;

public interface ILock {

    boolean tryLock(Long timeOut);

    void unlock();
}
