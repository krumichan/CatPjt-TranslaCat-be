# backend template
***

### 2. change setting
1. **open 'settings.gradle' and change value**
```text
rootProject.name = 'spring-boot-translacat'
```

2. **Invalidate Caches**
```text
1) File(파일)
2) Invalidate Caches...(캐쉬 무효화...) 
3-1) Check All
3-2) Invalidate and Restart(무효화 및 다시 시작)
```

3. **Enable Annotation Processing**
```text
1) File(파일)
2) Settings(설정)
3-1) Build, Execution, Deployment(빌드, 실행, 배포)
3-2) Compiler(컴파일러)
3-3) Annotation Processors(어노테이션 프로세서)
4-1) Check Enable annotation processing(어노테이션 처리 활성화)
4-2) Confirm(확인)
```

***

### 2. Swagger URL

* http://localhost:8080/swagger-ui/index.html
***

### 3. Hierarchy
```text
root
├─ global
│   ├─ config
│   │   ├─ QueryDslConfig.java
│   │   ├─ SecurityConfig.java
│   │   ├─ SwaggerConfig.java
│   │   └─ WebClientConfig.java
│   ├─ dto
│   │   ├─ ErrorDto.java
│   │   ├─ OrderDto.java
│   │   ├─ PageableDto.java
│   │   ├─ RequestContextDto.java
│   │   └─ ResponseDto.java
│   ├─ exception
│   │   └─ ApiExceptionAdvice.java
│   ├─ paging
│   │   └─ Pagination.java
│   ├─ security
│   │   ├─ JwtFilter.java
│   │   ├─ JWTService.java
│   │   ├─ MyUserDetailsService.java
│   │   ├─ Role.java
│   │   └─ UserPrincipal.java
│   └─ utils
│   │   ├─ ExceptionUtil.java
│   │   ├─ PagingUtil.java
│   │   ├─ QueryDslUtil.java
│   │   ├─ ResponseUtil.java
│   │   ├─ SecurityUtil.java
│   │   ├─ ServletRequestUtility.java
│   │   └─ SortUtility.java
├─ domain
│   ├─ base
│   │   ├─ Base.java
│   │   └─ BaseAuditable.java
│   ├─ example
│   │   ├─controller
│   │   │   └─ ExampleController.java
│   │   ├─dto
│   │   │   ├─ ExampleCreateRequestDto.java
│   │   │   ├─ ExampleDetailCreateRequestDto.java
│   │   │   ├─ ExampleDetailResponseDto.java
│   │   │   ├─ ExampleResponseDto.java
│   │   │   ├─ ExampleSearchRequestDto.java
│   │   │   └─ ExampleSearchResponseDto.java
│   │   ├─entity
│   │   │   ├─ Example.java
│   │   │   └─ ExampleDetail.java
│   │   ├─repository
│   │   │   ├─ ExampleDetailRepository.java
│   │   │   ├─ ExampleQueryRepository.java
│   │   │   ├─ ExampleQueryRepositoryImpl.java
│   │   │   └─ ExampleRepository.java
│   │   └─service
│   │       └─ ExampleService.java
│   ├─ external
│   │   ├─ gemini
│   │   │   ├─ controller
│   │   │   │   └─ ExampleAiGeminiController.java
│   │   │   └─ service
│   │   │       └─ ExampleAiGeminiService.java 
│   │   └─ legacy
│   │       ├─ controller
│   │       │   └─ WebController.java
│   │       ├─dto
│   │       │   ├─ ExternalCreateRequestDto.java
│   │       │   ├─ ExternalPostResponseDto.java
│   │       │   └─ ExternalResponseDto.java
│   │       └─service
│   │           └─ WebService.java
│   └─ user
│       ├─controller
│       │   └─ UserController.java
│       ├─dto
│       │   ├─ UserCreateRequestDto.java
│       │   ├─ UserLoginRequestDto.java
│       │   └─ UserLoginResponseDto.java
│       ├─entity
│       │   ├─ RefreshToken.java
│       │   └─ User.java
│       ├─repository
│       │   ├─ RefreshTokenRepository.java
│       │   └─ UserRepository.java
│       └─service
│           └─ UserService.java
├─ infrastructure
│   └─client
│       ├─ ai
│       │   └─ gemini
│       │       └─ AiGeminiClient.java
│       └─ legacy
│           └─ ExternalApiClient.java
└─ TranslacatApplication.java
```

***

### 4. Authentication Flow

```text
클라이언트 로그인 요청
│
▼
authenticationManager.authenticate(email, password)   ← maybe UserService.java
│
▼
DaoAuthenticationProvider.authenticate()
│
▼
MyUserDetailsService.loadUserByUsername(email)   ← DB 조회
│
▼
UserPrincipal(user) 반환
│
▼
비밀번호 비교(입력 vs DB)
│
├─ 성공 → Authentication 객체 반환 → SecurityContext에 저장
└─ 실패 → 예외 발생
```

***

### 5. Build Q-Class

1. **Clean And Build using Gradlew**
   ```bash
   .\gradlew clean build
   ```
