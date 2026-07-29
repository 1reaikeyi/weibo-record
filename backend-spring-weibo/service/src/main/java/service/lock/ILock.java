package service.lock;

public interface ILock {
    /**
     * 获取锁
     * @return
     */
    boolean getLocked(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
