package com.intheeast.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.intheeast.filters.CsrfCookieFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // CSRF 토큰 핸들러를 별도의 빈으로 등록
    @Bean
    public CsrfTokenRequestAttributeHandler csrfTokenRequestAttributeHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        // 속성 이름을 null로 설정하여 '지연된 토큰' 메커니즘을 끄고 즉시 토큰을 생성/갱신하도록 함
        /*
          set-cookie XSRF-TOKEN=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT

          set-cookie XSRF-TOKEN=805eb4f6-b6c4-402e-a107-fa99f15e5500; Path=/

          위 전송으로 인해 웹 브라우저는 쿠키를 먼저 삭제하고 쿠키를 다시 설정합니다.
		  만약 setCsrfRequestAttributeName 메서드에 null 아규먼트를 전달하지 않으면,
		  set-cookie XSRF-TOKEN=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT 만 전송해서 csrf 토큰이 유실됨.

		  1. 토큰을 폐기(갱신)하는 진짜 주체
		  토큰을 새로 만들거나 기존 것을 무효화하는 로직은 핸들러가 아니라 CsrfFilter와 CsrfTokenRepository가 담당합니다.
          - CsrfFilter: 요청이 들어오면 기존 토큰이 맞는지 검사합니다.
          - CsrfTokenRepository: 검사가 끝나면 "자, 이 토큰은 썼으니까 다음을 위해 새 토큰을 준비하자"라고 결정합니다.

          2. 핸들러의 "진짜" 디폴트 동작 (BREACH 방어)
          CsrfTokenRequestAttributeHandler의 기본 동작은 토큰을 폐기하는 것이 아니라, 보안을 위해 토큰을 매번 다르게 보이도록 "변장(Masking)" 시키는 것입니다.
          Spring Security 6의 기본 핸들러(사실은 XorCsrfTokenRequestAttributeHandler)는 원본 UUID 토큰에 무작위 값을 섞어(XOR 연산) 매 응답마다 겉모습이 다른 토큰을 보냅니다.
          - 클라이언트 입장에서는 토큰 값이 바뀌었으니 "기존 것이 폐기되고 새것이 왔다"고 느낄 수 있습니다.
          - 하지만 실제 서버 내부의 원본 UUID(Raw Token)는 그대로 유지되고 있을 수도 있습니다.

          3. 왜 csrf 토큰이 "폐기된 것처럼" 보일까요?
          "토큰 유실" 현상은 핸들러가 토큰을 폐기해서라기보다, "새 토큰을 보낼 타이밍을 놓쳐서" 발생하는 현상에 가깝습니다.
          - 지연 로딩(Default): 핸들러가 "나중에 누가 부르면 토큰 만들게"라고 미룹니다.
          - 검증 완료: POST 요청 검증이 끝납니다.
          - 응답 단계: 서버는 "검증 끝났으니 기존 쿠키는 지워야지(Max-Age=0)"라고 응답을 준비합니다.
          - 누락: 이때 핸들러가 새 토큰을 즉시 생성해서 Set-Cookie로 덮어써 줘야 하는데, 지연 로딩 모드라면 "아무도 새 토큰 달라고 안 했네? 그럼 그냥 삭제 명령만 보내자"하고 끝나버립니다.

          👉 그래서 브라우저에는 삭제 명령만 전달되고, 새 토큰은 오지 않아 유실되는 것입니다.

          4. setCsrfRequestAttributeName(null)의 진짜 의미
          이 설정을 하면 핸들러가 다음과 같이 행동합니다.
          - "지연 로딩 따위 안 해. 요청이 오면 무조건 원본 토큰을 즉시 꺼내서 보여줄 거야."
          - 이 덕분에 CsrfCookieFilter에서 .getToken()을 부르는 순간, 핸들러가 즉시 반응하여 응답 헤더에 새 쿠키(갱신된 토큰)를 확실히 실어 보내게 됩니다.
         */
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CsrfTokenRequestAttributeHandler handler) throws Exception {
        http
                // 1. 세션을 완전히 사용하지 않도록 설정 (Stateless): 실제 Double Submit Cookie 패턴이 실행되는지를 확인하기 위해서...
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // 2. CORS 설정: JS 코드가 동작하는 포트를 허용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. CSRF 설정: 쿠키 방식으로 전달
                // CookieCsrfTokenRepository는 디폴트로 쿠키에 SameSite 속성을 명시하지 않습니다.
                // 이 경우, 최신 브라우저(Chrome 80 버전 이후)는 보안을 위해 이 쿠키를 Lax로 간주합니다.
                // 프런트엔드(localhost:5501)와 백엔드(localhost:8080)는 Same-Site이기 때문에,
                // Lax 설정 상태에서도 브라우저가 쿠키를 정상적으로 서버에 실어 보내는 것.
                // 그리고 CookieCsrfTokenRepository의 Secure는 디폴트로 false 임
                // Set-Cookie: XSRF-TOKEN=...; Path=/
                // Path=/ 는 CookieCsrfTokenRepository의 cookiePath가 디폴트로 "/" 이기 때문에 Path=/로 설정됩니다.
                .csrf(csrf -> csrf
                                .ignoringRequestMatchers("/api/init") // 토큰 발급용 경로는 CSRF 검증 패스
                                // CookieCsrfTokenRepository는 기본적으로 매 응답마다 토큰을 갱신
                                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                // 이 핸들러는 디폴트로 토큰을 '지연'시킵니다. POST 요청이 성공한 후 응답이 나갈 때,
                                // 핸들러가 "이미 검증 끝난 토큰"이라고 판단하여 새 쿠키 생성을 무시할 수 있습니다.
//                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                                // 그러므로 수정을 합니다.
                                .csrfTokenRequestHandler(handler)
                )

                // 4. CSRF 토큰 발급을 강제하는 필터 추가
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        // 브라우저의 사전 검사(OPTIONS)는 인증 없이 무조건 통과시켜야 함
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/init").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults()); // 테스트 편의를 위한 basic 인증

        return http.build();
    }

    // CORS 상세 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 웹 브라우저의 주소창에서 http://localhost:5500/index.html 로 입력해서 테스트를 수행해야 합니다.
        // 이유는 README.md를 참조하세요
        config.setAllowedOrigins(List.of("http://localhost:5500"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        /*
         유저가 데이터 전송 버튼을 눌러 POST /api/data를 보낼 때, 브라우저 내부에서는 이런 대화가 오갑니다.
         1. 브라우저 (망설임): "어? 지금 8080 포트로 데이터를 보내는데, 헤더에 X-XSRF-TOKEN이라는 특이한 이름의 헤더를 실었네? 이거 서버가 싫어하면 어쩌지?"
         2. 브라우저 (물어보기 - OPTIONS): 서버야, 나 지금 Content-Type이랑 X-XSRF-TOKEN 헤더를 써서 POST를 보내고 싶은데, 너 이거 허용해주니? (이게 Pre-flight 요청입니다.)
         3. 서버 (응답 - setAllowedHeaders): 응, 내가 목록을 보니까 Content-Type, X-XSRF-TOKEN, Authorization은 허용된 헤더(AllowedHeaders) 리스트에 있네. 써도 좋아!
         4. 브라우저 (안심): 오케이! 그럼 이제 진짜 데이터랑 헤더를 실어서 진짜 POST 요청을 보낼게.
         */
        // For Double Submit Cookie
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("XSRF-TOKEN")); // 클라이언트 JS가 이 헤더를 볼 수 있게 허용(access-control-expose-headers XSRF-TOKEN)
        // Expose-Headers는 서버가 준 '특별한 선물(커스텀 헤더)'을 자바스크립트가 포장을 뜯어서 확인할 수 있게 해주는 '개봉 허가증'
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // 별도의 핸들러 빈 또는 메서드 생성
    private CsrfTokenRequestAttributeHandler requestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        // 이 이름을 null로 설정하면 지연된 토큰이 아니라 즉시 로드되는 토큰으로 동작하여
        // POST 응답 시에도 쿠키가 누락되지 않고 갱신됩니다.
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }


    /*
     1. HTTP Basic 인증의 비밀: 브라우저의 기억력
	 HTTP Basic 인증은 서버가 세션을 생성해서 유지하는 게 아니라, 브라우저가 스스로 인증 정보를 관리합니다.
	  1. 최초 요청: 브라우저가 /api/data를 호출합니다. (인증 정보 없음)
	  2. 서버의 응답: 401 Unauthorized와 함께 WWW-Authenticate: Basic ... 헤더를 보냅니다.
	  3. 브라우저의 팝업: 브라우저 자체 로그인 창이 뜹니다. 사용자가 아이디/비밀번호를 입력합니다.
	  4. 인증 정보 저장: 브라우저는 입력받은 정보를 Base64로 인코딩하여 자신의 메모리(비공개 영역)에 저장합니다. (Authorization: Basic YWRtaW46MTIzNA==)
	  5. 자동 주입 (핵심): 이후 같은 도메인(localhost:8080)으로 가는 모든 요청에 대해, 브라우저가 알아서 Authorization 헤더를 붙여서 보냅니다.

	  * Basic 인증을 쓰면 "로그아웃 버튼"을 만들기 어려운 이유가 바로 이겁니다.
	    서버에서 세션을 지워봤자, 브라우저 메모리에 저장된 신분증이 다음 요청 때 또 자동으로 날아가기 때문에 다시 로그인이 되어버립니다.
          팁: 로그아웃을 하려면 브라우저를 완전히 닫거나, 캐시를 날려야 합니다.

      * "한 번만 인증해도 되는 것"처럼 보이는 건 서버가 똑똑해서가 아니라,
        브라우저가 사용자 몰래 매번 아이디/비밀번호를 Authorization 헤더에 'Basic '+Base64 인코딩 값을서버에 전달하고 있기 때문
     */

}