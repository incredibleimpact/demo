package com.example.demo.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheEvictMessage implements Serializable {
    private String msgId;
    private String cacheKey;
}
