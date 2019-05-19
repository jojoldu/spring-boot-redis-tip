# Jedis 보다 Lettuce 를 쓰자

Java의 Redis Client는 크게 2가지가 있습니다.

* [Jedis](https://github.com/xetorthio/jedis)
* [Lettuce](https://github.com/lettuce-io/lettuce-core)

둘 모두 몇천개의 Star를 가질만큼 유명한 오픈소스입니다.  
이번 시간에는 둘 중 어떤것을 사용해야할지에 대해 **성능 테스트 결과**를 공유하고자 합니다.

> 모든 코드와 Beanstalk 설정값은 [Github](https://github.com/jojoldu/spring-boot-redis-tip)에 있으니 참고하세요.

> 레디스외 병목현상을 방지하기 위해 Nginx, 커널 파라미터 등은 모두 적절하게 [튜닝](https://github.com/jojoldu/spring-boot-redis-tip/tree/master/.ebextensions)된 상태입니다.

## 0. 프로젝트 환경

의존성 환경은 아래와 같습니다.

* Spring Boot 2.1.4
* Spring Boot Data Redis 2.1.4
* Jedis 2.9.0
* Lettuce 5.1.6

그리고 테스트에 사용될 Redis Entity 코드는 아래와 같습니다.

```java
@ToString
@Getter
@RedisHash("availablePoint")
public class AvailablePoint implements Serializable {

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
}
```

```java
public interface AvailablePointRedisRepository extends CrudRepository<AvailablePoint, String> {
}
```

임의의 데이터를 저장하고, 가져올 ```Controller``` 코드는 아래와 같습니다.

```java
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

    // 임의의 키를 생성하기 위해 1 ~ 1_000_000_000 사이 랜덤값 생성
    private String createId() {
        SplittableRandom random = new SplittableRandom();
        return String.valueOf(random.nextInt(1, 1_000_000_000));
    }
}
```

위 ```/save``` API를 통해 테스트할 데이터를 Redis에 적재해서 사용합니다.  
성능 테스트는 ```/get``` API를 통해 진행합니다.  

### 0-1. EC2 사양

Spring Boot가 실행되고 Redis로 요청할 EC2의 사양은 아래와 같습니다.

![ec2](./images/ec2.png)

(최대한 Redis 자원을 사용하기 위해 높은 사양을 선택했습니다.)

* R5d.4xLarge X 4대

### 0-2. Redis 사양

테스트에 사용될 Redis (Elastic Cache) 의 사양은 아래와 같습니다.

![spec](images/spec.png)

* R5.large
* Redis 5.0.3

테스트용 데이터는 대략 **1천만건**을 적재하였습니다.

![count](./images/count.png)

자 그럼 먼저 Jedis를 먼저 테스트해보겠습니다.

## 1. Jedis

Jedis는 예전부터 Java의 표준 Redis Client로 사용되었습니다.  
그래서 대부분의 Java & Redis 예제가 Jedis로 되어 있는데요.  
여기서도 기본적인 Jedis 설정만 사용하겠습니다.

```java
@RequiredArgsConstructor
@Configuration
@EnableRedisRepositories
public class RedisRepositoryConfig {
    private final RedisProperties redisProperties;

    //jedis
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new JedisConnectionFactory(new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort()));
    }

    @Bean
    public RedisTemplate<?, ?> redisTemplate() {
        RedisTemplate<byte[], byte[]> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        return redisTemplate;
    }
}
```

build.gradle에서는 **기본 의존성인 lettuce를 제거**하고 Jedis를 등록합니다.

> Spring Boot 2.0이 되고 lettuce가 기본 클라이언트가 되어서 아래와 같이 제거해야만 합니다.

```groovy
dependencies {
    compile group: 'it.ozimov', name: 'embedded-redis', version: '0.7.2'

    // jedis
    compile group: 'redis.clients', name: 'jedis'
    compile group: 'org.apache.commons', name: 'commons-pool2', version: '2.6.2'
    compile ('org.springframework.boot:spring-boot-starter-data-redis') {
        exclude group: 'io.lettuce', module: 'lettuce-core'
    }

    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

```

정상적으로 수행하셨다면 첫번째 테스트를 시작해보겠습니다.

### 1-1. Not Connection Pool

먼저 Connection Pool 설정 없이 테스트해보겠습니다.

> 참고로 모든 테스트는 [JVM의 Warm up Time](https://dzone.com/articles/why-many-java-performance-test)을 고려하여 2번이상 테스트하였습니다.


VUser 740명으로 시도합니다.

* agent는 5대
* 각 agent 별 148명을 지정했습니다.
* 부하 테스트 시간은 3분

Agent

![jedis-agent1](./images/jedis-agent1.png)

핀포인트 결과는 평균 **125ms**가 나왔습니다.

![jedis-pinpoint](./images/jedis-pinpoint.png)

![jedis-connection](./images/jedis-connection.png)

![jedis-result-740](./images/jedis-result-740.png)


> 제가 사내 시스템에 Redis를 적용하던 시점에는 Jedis가 더이상 업데이트되지 않고 있었습니다.  
2016년 9월이후로 더이상 릴리즈 되지 않다가, 2018년 11월부터 다시 릴리즈가 되고 있습니다.  
혹시나 jedis를 사용하실 분들은 [릴리즈 노트](https://github.com/xetorthio/jedis/releases)를 참고해보세요.

### 1-2. Use Connection Pool

Connection Pool을 사용하지 않으니 

* TPS가 생각보다 낮게 나왔습니다.
* Redis Connection과 EC2 서버의 CPU가 여유로웠습니다.

```java
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort());
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(config);
        jedisConnectionFactory.setPoolConfig(jedisPoolConfig());
        return jedisConnectionFactory;
    }

    private JedisPoolConfig jedisPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(36);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        return poolConfig;
    }
```

* Jedis는 최대 Pool Size를 초과해서 Connection을 맺을 수 있다.
* Jedis의 경우 TP

문제는 **이보다 더 높은 TPS를 맞추려면 Redis의 CPU가 90%를 넘을수도** 있습니다.  
  
이건 충분히 문제가 되겠죠?  
  
Jedis를 **꼭** 써야한다면 성능 테스트를 통해 **적절한 Pool Size**를 찾아보셔야만 합니다.

## 2. Lettuce

다음으로 Lettuce로 설정을 변경후 테스트해보겠습니다.

Lettuce

```java
@RequiredArgsConstructor
@Configuration
@EnableRedisRepositories
public class RedisRepositoryConfig {
    private final RedisProperties redisProperties;

    // lettuce
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(redisProperties.getHost(), redisProperties.getPort());
    }

    @Bean
    public RedisTemplate<?, ?> redisTemplate() {
        RedisTemplate<byte[], byte[]> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory());
        return redisTemplate;
    }
}
```

build.gradle

```groovy
dependencies {
    compile group: 'it.ozimov', name: 'embedded-redis', version: '0.7.2'
    // lettuce
    compile ('org.springframework.boot:spring-boot-starter-data-redis')

    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

자 이렇게 설정후 테스트를 해보면!

TPS는 무려 **10만**을 처리합니다.

![lettuce-result-740](./images/lettuce-result-740.png)

실제로 워낙 빠르게 처리하다보니 **대량의 요청을 해야하는 agent CPU가 70%까지** 올라갔습니다

![lettuce-agent1](./images/lettuce-agent1.png)

Connection은 **6-7**개를 유지합니다.

![lettuce-connection](./images/lettuce-connection.png)


CPU는 **7**%밖에 되지 않습니다.

![lettuce-cpu](./images/lettuce-cpu.png)





Jedis는 애플리케이션이 Jedis여러 스레드 에서 단일 인스턴스 를 공유하려고 할 때 스레드로부터 안전하지 않은 직선적 인 Redis 클라이언트입니다. 
멀티 쓰레드 환경에서 Jedis를 사용하는 접근법은 Connection Pool을 사용하는 것입니다.
 
Jedis를 사용하는 각 스레드는 Jedis는 Jedis상호 작용 의 지속 기간 동안 자체 인스턴스를 가져 옵니다. 
연결 풀링은 Jedis인스턴스 당 물리적​​인 연결 비용으로 인해 Redis 연결 수를 증가시킵니다.
Redis에서 연결 제한을 사용하거나 합리적인 연결 수를 초과하여 연결 수가 증가하는 경우 연결 수를 제한해야 할 수 있습니다.

Lettuce는 네티 위에 만들어지며 연결 인스턴스 ( StatefulRedisConnection)는 여러 스레드에서 공유 할 수 있습니다. 따라서 멀티 스레드 응용 프로그램은
Lettuce와 상호 작용하는 동시 스레드 수에 관계없이 단일 연결을 사용할 수 있습니다 .



## 참고

* [Why is Lettuce the default Redis client used in Spring Session Redis](https://github.com/spring-projects/spring-session/issues/789)