package com.jojoldu.blogcode.springbootredistip.point;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;
import java.time.LocalDateTime;

@ToString
@Getter
@RedisHash("availablePoint")
public class AvailablePoint implements Serializable {
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Id
    private String id; // userId
    private Long point;
    private LocalDateTime refreshTime;

    @Builder
    public AvailablePoint(String id, Long point, LocalDateTime refreshTime) {
        this.id = id;
        this.point = point;
        this.refreshTime = refreshTime;
    }

    public AvailablePoint refresh(Long point, LocalDateTime refreshTime) {
        this.point = point;
        this.refreshTime = refreshTime;
        return this;
    }


}