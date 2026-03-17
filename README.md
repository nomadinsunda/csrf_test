# 📌 주의: localhost vs 127.0.0.1 때문에 쿠키가 안 보이는 문제

## 1️⃣ 문제 상황

웹 브라우저에서 다음 주소로 테스트해야 합니다.

```
http://localhost:5500/index.html
```

하지만 **Live Server**가 자동 실행될 때는 보통 다음 주소로 열립니다.

```
http://127.0.0.1:5500/index.html
```

이 두 주소는 **사람이 보기에는 같은 서버처럼 보이지만 브라우저 입장에서는 다른 Origin**입니다.

---

# 2️⃣ 왜 문제가 발생하는가

브라우저의 **쿠키는 Origin(도메인) 기준으로 저장**됩니다.

즉 다음처럼 저장됩니다.

| 접속 주소                   | 쿠키 저장 위치         |
| ----------------------- | ---------------- |
| `http://127.0.0.1:5500` | 127.0.0.1 도메인 쿠키 |
| `http://localhost:5500` | localhost 도메인 쿠키 |

따라서

1️⃣ Live Server가

```
http://127.0.0.1:5500
```

로 실행됨

2️⃣ 서버는 **127.0.0.1 기준 쿠키**를 생성

3️⃣ 그런데 JS 코드에서

```javascript
document.cookie
```

를 읽을 때 브라우저 주소가

```
http://localhost:5500
```

이면

👉 브라우저는 **localhost 쿠키 저장소**를 확인합니다.

하지만 쿠키는

```
127.0.0.1 쿠키 저장소
```

에 있기 때문에

➡️ **쿠키가 없는 것처럼 보입니다.**

---

# 3️⃣ 결과

그래서 다음 문제가 발생합니다.

```
document.cookie 에 CSRF 토큰이 없음
→ CSRF 토큰을 요청 헤더에 넣지 못함
→ 서버 요청 실패
```

---

# 4️⃣ 해결 방법

### 방법 1️⃣ 주소를 통일

항상 동일한 주소로 실행

예

```
http://localhost:5500/index.html
```

또는

```
http://127.0.0.1:5500/index.html
```

둘 중 하나만 사용

---

### 방법 2️⃣ Live Server 설정 변경

VSCode Live Server 설정에서

```
127.0.0.1 → localhost
```

로 실행되게 설정

---

### 방법 3️⃣ Spring Security 설정 
```
@Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        
        config.setAllowedOrigins(List.of("http://localhost:5500"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("XSRF-TOKEN")); // 클라이언트 JS가 이 헤더를 볼 수 있게 허용(access-control-expose-headers XSRF-TOKEN)
        // Expose-Headers는 서버가 준 '특별한 선물(커스텀 헤더)'을 자바스크립트가 포장을 뜯어서 확인할 수 있게 해주는 '개봉 허가증'
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
```


# 🎯 핵심 요약

```
localhost ≠ 127.0.0.1
```

브라우저에서는 **다른 도메인**으로 취급된다.

따라서

```
127.0.0.1 에 저장된 쿠키는
localhost 에서 읽을 수 없다.
```
