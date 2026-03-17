package com.intheeast.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.ResponseCookie;

import java.io.IOException;

public class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // CSRF 토큰을 속성에서 꺼내어 .getToken()을 호출하면 쿠키가 생성됨
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
        	/*
        	 "잠자고 있는 Spring Security의 CSRF 엔진을 강제로 깨우는 알람" 같은 역할을 합니다.
              1. Spring Security 6의 "지연된 로딩(Deferred Loading)"
                 Spring Security 6부터는 성능 최적화를 위해 CSRF 토큰을 **"진짜 필요할 때까지 만들지 말자"**는 원칙을 가집니다.
                 - request.getAttribute()로 토큰 객체를 가져와도, 이 객체는 실제 값이 담긴 토큰이 아니라 나중에 필요할 때 값을 만들어줄 대리인(Supplier)일 뿐입니다.
                 - 만약 아무도 토큰 값을 물어보지 않는다면, Spring은 응답 헤더에 Set-Cookie를 아예 굽지 않고 요청을 끝내버립니다.
              2. getToken(): "지금 당장 값을 만들어!" (강제 실행)
                 이때 csrfToken.getToken()을 호출하면 다음과 같은 연쇄 반응이 일어납니다.
                 - 계산 시작: "누가 값을 달라고 하네? 이제 진짜 UUID 토큰을 생성해야겠다."
                 - 저장소 기록: 생성된 토큰을 CookieCsrfTokenRepository에 전달합니다.
                 - 응답 예약: Repository는 이 값을 바탕으로 Set-Cookie: XSRF-TOKEN=... 헤더를 생성하여 응답(HttpServletResponse)에 실어달라고 예약합니다.

                 결론적으로: 이 메서드를 호출하지 않으면 서버는 쿠키를 아예 생성하지 않거나, POST 요청 성공 후 기존 쿠키를 삭제한 뒤 새로운 쿠키를 다시 구워주지 않는 문제가 발생합니다.
              3. 왜 CsrfCookieFilter에서 이 작업을 할까요?
                 보통 Spring Security는 POST 요청에서만 CSRF 토큰을 확인합니다. 하지만 우리는 GET 요청(예: /api/init)에서도 클라이언트에게 쿠키를 구워줘야 하죠.
                 - GET 요청은 원래 CSRF 검사를 안 하기 때문에, Spring Security는 토큰을 만들 생각조차 안 합니다.
                 - 그래서 우리가 만든 CsrfCookieFilter가 모든 요청(Every Request) 마다 끼어들어서 **"야, 혹시 모르니까 토큰 값 좀 미리 확인해놔(getToken)"**라고 옆구리를 찌르는 것입니다.
                 - 덕분에 응답이 나갈 때 항상 최신 상태의 XSRF-TOKEN 쿠키가 브라우저에 배달될 수 있는 것입니다.

             ** csrfToken.getToken()은 Spring Security에게 "이 요청이 끝나기 전에 무조건 CSRF 쿠키를 생성해서 응답에 실어 보내라"고 명령하는 스위치입니다.
        	 */
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}