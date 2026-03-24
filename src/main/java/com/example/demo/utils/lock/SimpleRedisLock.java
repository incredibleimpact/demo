package com.example.demo.utils.lock;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX="lock";
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId=ID_PREFIX+Thread.currentThread().getId();
        //SET lock:name id EX timeoutSec NX
        Boolean success=stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name,threadId,timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void delLock(){ //Redis执行UNLOCK脚本,传入参数(锁,线程id)
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),//锁名称,通过KEYS[1]获取
                ID_PREFIX+Thread.currentThread().getId()//线程id,通过ARGV[1]获取
        );
    }

//    @Override
//    public void delLock() {
//        String threadId=ID_PREFIX+Thread.currentThread().getId();
//        String id=stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
//        if(threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
//    }
}