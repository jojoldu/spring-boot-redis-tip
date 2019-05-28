# keys는 몇건부터 문제가 있을까?

아래는 [강대명님의 Redis 운영 관리](https://coupa.ng/bhcBtj) 라는 책의 한 구절입니다.

> Redis 운영 관리 P.12
일례로 Redis를 사용하는 한 회사에서 필자에게 문의해온적이 있다.  
데이터양이 적을때는 Redis가 굉장히 빠르다가, **데이터가 10만개에서 20만개가 되면서는 속도가 느려지기 시작했고**, 데이터 양이 늘어날 수록 속도가 점점 느려진다는 것이였다.  
해당 서비스를 살펴보니, 서비스에서 필요한 Key 목록을 keys 명령을 통해서 가져오고 있었다.

Redis가 장애나면 무조건 keys 쓴 코드가 잘못됐다고 할때가 있습니다.
무조건 keys쓴게 잘못된것이긴 합니다만 **실제 성능 이슈가 key가 아닌 다른 이슈일수도 있는데**, 무조건 keys 탓으로 해버리면 **또 같은 문제가 발생할수 있다**는게 문제입니다.  
정확한 원인 파악이 제일 중요합니다.

만약 100건 이하의 key만 있다면?  
1만건 이하라면?  
keys가 문제가 될까요?  
  
그래서 이번 시간에는 Redis의 **keys는 몇건부터 이슈가 발생할까** 확인해보겠습니다.

## 0. 프로젝트 환경

테스트 환경은 앞서 [테스트한 글](https://jojoldu.tistory.com/418)과 같습니다.

* Spring Boot 2.1.5
* Spring Boot Data Redis 2.1.5
* Jedis 2.9.0
* Lettuce 5.1.6



