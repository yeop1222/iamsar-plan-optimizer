# IAMSAR 수색계획 최적화 검증 프로그램

IAMSAR(International Aeronautical and Maritime Search and Rescue) 매뉴얼 기반의 수색계획 수립 및 최적화를 지원하는 웹 애플리케이션입니다. 조난 추정 위치(데이텀)와 수색 자원(SRU) 정보를 입력하면, 발견 확률(POS)을 극대화하는 수색 영역을 자동으로 계산합니다.

## 주요 기능

- **데이텀 정의** — POINT / LINE 유형의 조난 추정 위치 및 오차 입력, Gaussian 기반 POC(억류 확률) 래스터 자동 생성
- **SRU 관리** — 선박·항공기 유형별 탐색폭(Sweep Width) 자동 계산, 환경조건(시정·풍속·파고) 및 탐색 대상 반영
- **수색계획 수립** (3가지 모드)
  - **Baseline** — IAMSAR Vol.II L-7 워크시트 기반 자동 생성
  - **Manual** — 지도 위에 수색 영역을 직접 그리고 실시간 POS 평가
  - **GA 최적화** — 유전 알고리즘으로 수색 영역 배치·크기·방향 자동 최적화
- **결과 분석** — SRU별 POC / Coverage Factor / POD / POS 분석, 다중 고도 시너지 계산
- **결과 저장** — 수색계획 결과 누적 저장 및 import/export 지원

## 스크린샷

> 아래 스크린샷은 추후 추가 예정입니다.

### 메인 화면
<!-- ![메인 화면](assets/screenshots/main.png) -->
`📷 스크린샷 추가 예정`

### 데이텀 입력 및 POC 래스터
<!-- ![데이텀 입력](assets/screenshots/datum-poc.png) -->
`📷 스크린샷 추가 예정`

### SRU 등록 및 탐색폭 계산
<!-- ![SRU 등록](assets/screenshots/sru.png) -->
`📷 스크린샷 추가 예정`

### Baseline 수색계획 생성
<!-- ![Baseline 수색계획](assets/screenshots/baseline.png) -->
`📷 스크린샷 추가 예정`

### GA 최적화 결과
<!-- ![GA 최적화](assets/screenshots/ga-optimize.png) -->
`📷 스크린샷 추가 예정`

### 수색계획 결과 비교
<!-- ![결과 비교](assets/screenshots/result-compare.png) -->
`📷 스크린샷 추가 예정`

## 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.0.5 |
| Frontend | Thymeleaf, jQuery, OpenLayers 7.2.2 |
| 지리 연산 | JTS Core 1.20.0 (Geometry), Vincenty 측지선 계산 |
| 최적화 | 유전 알고리즘 (SBX 교차, 가우시안 변이, 토너먼트 선택) |
| 빌드 | Gradle 8.x |

## 실행 방법

### 요구사항

- JDK 17 이상

### 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun
```

브라우저에서 `http://localhost:8080` 으로 접속합니다.

## 프로젝트 구조

```
src/main/java/papervalidation/
├── controller/rest/       # REST API (datum, sru, raster, search-plan)
├── domain/                # 도메인 모델
│   ├── datum/             #   데이텀 (DatumType, DatumPoint, DatumSet)
│   ├── sru/               #   수색구조단위 (Sru, SruType, EnvironmentCondition)
│   ├── raster/            #   POC 래스터 (PocRaster, SamplePoint)
│   ├── searchplan/        #   수색영역 및 결과 (SearchArea, SearchPlanResult)
│   └── ga/                #   유전 알고리즘 (Chromosome, SruGene, GaConfig)
├── service/
│   ├── raster/            #   POC 래스터 생성 (Gaussian 샘플링 전략)
│   ├── searchplan/        #   Baseline(L-7), Manual 평가, POS 계산
│   ├── ga/                #   GA 최적화 엔진
│   └── sru/               #   SRU 관리 및 Sweep Width 테이블
└── util/                  # 좌표 변환, 측지선 계산

src/main/resources/
├── static/js/             # 프론트엔드 모듈 (datum, sru, plan, map)
└── templates/             # Thymeleaf 템플릿
```

## 핵심 알고리즘

### IAMSAR L-7 Baseline

데이텀 오차(E)와 총 수색 노력(Z_ta)으로부터 최적 수색 인자(fs)를 산출하고, 이를 기반으로 최적 수색 영역과 트랙 간격을 결정합니다.

| 데이텀 | 조건 | 수색 인자 공식 |
|--------|------|---------------|
| POINT | Normal | fs = 0.7179 × Zr^0.2570 |
| POINT | Ideal | fs = 0.6396 × Zr^0.3348 |
| LINE | Normal | fs = 0.8204 × Zr^0.3411 |
| LINE | Ideal | fs = 0.7284 × Zr^0.5012 |

### GA 최적화

- **표현**: SRU별 6차원 유전자 (중심좌표, 너비/높이, 회전각, 고도)
- **적합도**: totalPOS - 중첩 패널티 - 소프트 제약 패널티
- **연산**: SBX 교차(η=20), 가우시안 변이, 엘리트 보존(상위 2개)

## API 요약

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST/GET/DELETE | `/api/datum` | 데이텀 CRUD |
| POST/GET/PUT/DELETE | `/api/sru` | SRU 관리 |
| POST/GET | `/api/conditions` | 환경·탐색조건 |
| POST | `/api/raster/generate` | POC 래스터 생성 |
| GET | `/api/raster/image` | POC 히트맵 이미지 |
| POST | `/api/search-plan/baseline/generate` | L-7 베이스라인 생성 |
| POST | `/api/search-plan/ga/optimize` | GA 최적화 실행 |
| POST | `/api/search-plan/manual/evaluate` | 수동 수색영역 평가 |

## 로드맵

### IAMSAR 표류예측 모듈 ([#4](https://github.com/yeop1222/iamsar-plan-optimizer/issues/4))
- [ ] TWC(해류) / Leeway(풍압류) 벡터 이산 시간 누적 기반 데이텀 위치 자동 산출
- [ ] IAMSAR 오차 전파 모델 (E = √(X² + Y² + D_e²)) 구현
- [ ] Leeway 발산(좌/우) 처리 및 다중 데이텀 생성
- [ ] 기상/해양 데이터 어댑터 (ECMWF/GFS 풍속, HYCOM 해류)

## 라이선스

이 프로젝트는 학술 연구 및 검증 목적으로 개발되었습니다.