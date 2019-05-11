package com.jojoldu.blogcode.springbootredistip.controller;

import com.jojoldu.blogcode.springbootredistip.point.AvailablePoint;
import com.jojoldu.blogcode.springbootredistip.point.AvailablePointRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.SplittableRandom;

@Slf4j
@RequiredArgsConstructor
@RestController
public class ApiController {
    private final AvailablePointRedisRepository availablePointRedisRepository;

    @GetMapping("/")
    public String ok () {
        return "ok";
    }

    @GetMapping("/save")
    public String save(){
        String randomId = createId();
        LocalDateTime now = LocalDateTime.now();

        AvailablePoint availablePoint = AvailablePoint.builder()
                .id(randomId)
                .point(1L)
                .refreshTime(now)
                .build();

        log.info(">>>>>>> [save] availablePoint={}", availablePoint);

        availablePointRedisRepository.save(availablePoint);

        return "save";
    }


    @GetMapping("/get")
    public long get () {
        String id = createId();
        return availablePointRedisRepository.findById(id)
                .map(AvailablePoint::getPoint)
                .orElse(0L);
    }

    private String createId() {
        SplittableRandom random = new SplittableRandom();
        return String.valueOf(random.nextInt(1, 1_000_000_000));
    }
}
