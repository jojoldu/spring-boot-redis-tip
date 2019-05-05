# Jedis 보다 Lettuce 를 쓰자

Jedis는 
Jedis는 애플리케이션이 Jedis여러 스레드 에서 단일 인스턴스 를 공유하려고 할 때 스레드로부터 안전하지 않은 직선적 인 Redis 클라이언트입니다. 
멀티 쓰레드 환경에서 Jedis를 사용하는 접근법은 Connection Pool을 사용하는 것입니다.
 
Jedis를 사용하는 각 스레드는 Jedis는 Jedis상호 작용 의 지속 기간 동안 자체 인스턴스를 가져 옵니다 . 연결 풀링은 Jedis인스턴스 당 물리적 ​​인 연결 비용으로 인해 Redis 연결 수를 증가시킵니다.
Redis에서 연결 제한을 사용하거나 합리적인 연결 수를 초과하여 연결 수가 증가하는 경우 연결 수를 제한해야 할 수 있습니다.

Lettuce는 네티 위에 만들어지며 연결 인스턴스 ( StatefulRedisConnection)는 여러 스레드에서 공유 할 수 있습니다. 따라서 멀티 스레드 응용 프로그램은
Lettuce와 상호 작용하는 동시 스레드 수에 관계없이 단일 연결을 사용할 수 있습니다 .



## 참고

* [Why is Lettuce the default Redis client used in Spring Session Redis](https://github.com/spring-projects/spring-session/issues/789)