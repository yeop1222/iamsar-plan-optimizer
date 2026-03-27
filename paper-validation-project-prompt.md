<!--
IAMSAR + GA 수색계획 최적화 논문 검증 프로그램 — 프로젝트 빌드 프롬프트
이 파일은 Markdown 형식이지만, hook 제한으로 .html 확장자로 저장됨.
실제 사용 시 .md로 이름 변경 가능.
-->

# IAMSAR + GA 수색계획 최적화 논문 검증 프로그램 — 프로젝트 빌드 프롬프트

## 1. 프로젝트 개요

IAMSAR 표류예측과 유전 알고리즘(GA) 기반 해양 수색계획 최적화 논문의 결과를 검증하기 위한 **독립 실행형 웹 애플리케이션**이다.

### 핵심 목적
- 사용자가 **데이텀(점/선/면)과 오차**를 직접 정의하고
- **POC(포함확률) 히트맵**을 생성한 뒤
- **3가지 방식**(수동 배치 / GA 최적화 / IAMSAR Baseline)으로 수색계획을 수립하고
- 각 방식의 **POS(수색 성공확률)**를 비교 검증한다

### 핵심 제약
- **DB 없음** — 모든 데이터는 인메모리/하드코딩
- **표류경로 예측 없음** — 사용자가 데이텀 결과(좌표 + 오차)를 직접 입력
- **단일 타임 스냅샷** — 래스터는 한 장만 생성

---

## 2. 기술 스택

| 구분 | 기술 | 비고 |
|------|------|------|
| Language | Java 17 | |
| Framework | Spring Boot 3.0.x | |
| Build | Gradle (Groovy DSL) | |
| Template | Thymeleaf + Thymeleaf Layout Dialect | |
| Frontend | ES6 Modules (번들러 없음), jQuery 3.6.x, OpenLayers 7.2.x | |
| GIS 라이브러리 | GeoTools 27.x (gt-geotiff, gt-geojson), JTS Core 1.20.x | |
| 좌표 변환 | proj4js (프론트), CoordinateConverter(백엔드) | |
| 템플릿 엔진 | Handlebars 4.7.x (클라이언트 사이드) | |
| 지리연산 | turf.js 7 (프론트) | |
| 기타 | Lombok, ModelMapper, SLF4J | |

### 프론트엔드 라이브러리 (CDN 또는 static)
- jQuery 3.6.3
- OpenLayers 7.2.2
- proj4.js
- Handlebars 4.7.7
- turf.js 7
- Font Awesome 6.4.0

---

## 3. 프로젝트 구조

```
paper-validation/
├── build.gradle
├── settings.gradle
├── src/main/java/papervalidation/
│   ├── PaperValidationApplication.java
│   ├── config/
│   │   └── WebConfig.java
│   ├── controller/
│   │   ├── MainController.java              # Thymeleaf 뷰
│   │   └── rest/
│   │       ├── DatumRestController.java      # 데이텀 관리 API
│   │       ├── RasterRestController.java     # POC 래스터화 API
│   │       ├── SearchPlanRestController.java  # 수색계획 API
│   │       └── SruRestController.java        # SRU 관리 API
│   ├── domain/
│   │   ├── datum/
│   │   │   ├── DatumType.java               # enum: POINT, LINE, AREA
│   │   │   ├── DatumPoint.java              # 위도, 경도, errorNm
│   │   │   └── DatumSet.java                # 데이텀 집합 (type + List&lt;DatumPoint&gt;)
│   │   ├── raster/
│   │   │   ├── PocRaster.java               # POC 래스터 데이터 (double[][] + 메타)
│   │   │   └── SamplePoint.java             # 래스터화용 샘플점 (x, y, sigma, weight)
│   │   ├── sru/
│   │   │   ├── SruType.java                 # enum: SHIP, ROTARY, FIXED
│   │   │   ├── Sru.java                     # 수색자원 도메인
│   │   │   └── SearchObjectType.java        # enum: PIW, RAFT, POWERBOAT, SAILBOAT, SHIP
│   │   ├── searchplan/
│   │   │   ├── SearchArea.java              # 수색구역 (cx, cy, w, h, rotation, sruId)
│   │   │   ├── SearchPlanResult.java        # 계획 결과 (POS, POD, POC per SRU)
│   │   │   └── BaselinePlan.java            # IAMSAR Baseline 결과
│   │   └── ga/
│   │       ├── GaConfig.java                # GA 파라미터
│   │       ├── Chromosome.java              # 개체 (SruGene[])
│   │       └── SruGene.java                 # 6D 유전자
│   ├── service/
│   │   ├── datum/
│   │   │   └── DatumService.java            # 데이텀 인메모리 관리
│   │   ├── raster/
│   │   │   ├── RasterService.java           # POC 래스터화
│   │   │   └── SamplingStrategy.java        # 데이텀 유형별 샘플링 (인터페이스)
│   │   ├── sru/
│   │   │   ├── SruService.java              # SRU 인메모리 관리
│   │   │   └── SweepWidthTable.java         # 하드코딩 탐지폭 테이블
│   │   ├── searchplan/
│   │   │   ├── ManualPlanService.java        # 수동 배치 POS 계산
│   │   │   ├── BaselinePlanService.java      # IAMSAR Baseline 배치
│   │   │   └── PosCalculator.java            # POS/POD/POC 계산 공통
│   │   └── ga/
│   │       ├── GaOptimizer.java             # GA 메인 엔진
│   │       ├── FitnessEvaluator.java        # 적합도 함수
│   │       ├── SelectionOperator.java       # 토너먼트 선택
│   │       ├── CrossoverOperator.java       # SBX 교차
│   │       ├── MutationOperator.java        # 가우시안 변이
│   │       └── OverlapRepairer.java         # 중첩 수리
│   └── util/
│       ├── CoordinateConverter.java         # EPSG:4326 ↔ 3857
│       └── VincentyUtils.java              # Vincenty 측지 공식
├── src/main/resources/
│   ├── application.yml
│   ├── templates/
│   │   ├── layout/
│   │   │   └── default.html                 # 공통 레이아웃
│   │   └── main/
│   │       └── index.html                   # 메인 페이지
│   └── static/
│       ├── css/
│       │   └── main.css                     # 전체 스타일
│       ├── js/
│       │   ├── app.js                       # 메인 진입점
│       │   ├── map/
│       │   │   ├── mapInit.js               # OpenLayers 초기화
│       │   │   ├── datumLayer.js            # 데이텀 레이어
│       │   │   ├── pocLayer.js              # POC 히트맵 레이어
│       │   │   └── searchAreaLayer.js       # 수색구역 레이어 (수동 드로잉 포함)
│       │   ├── datum/
│       │   │   └── datumManager.js          # 데이텀 CRUD UI
│       │   ├── sru/
│       │   │   └── sruManager.js            # SRU 등록/관리 UI
│       │   ├── plan/
│       │   │   ├── manualPlan.js            # 수동 배치 모드
│       │   │   ├── gaPlan.js                # GA 최적화 모드
│       │   │   └── baselinePlan.js          # IAMSAR Baseline 모드
│       │   └── util/
│       │       └── coordinate.js            # 좌표 변환 유틸
│       └── lib/                             # 외부 라이브러리 (CDN 대체 가능)
```

---

## 4. UI 레이아웃

### 전체 구조
```
┌──────────────────────────────────────────────────────┐
│  Header Bar (50px, #05205F)                          │
│  "IAMSAR Search Plan Optimizer"                      │
├────────────┬─────────────────────────────────────────┤
│            │                                         │
│  Left      │         Map Area                        │
│  Panel     │         (OpenLayers)                    │
│  360px     │                                         │
│            │   - 데이텀 표시                          │
│  ┌──────┐  │   - POC 히트맵 오버레이                  │
│  │ Tabs │  │   - SRU 수색 구역 표시                   │
│  │      │  │   - 수동 배치 드로잉                     │
│  │      │  │                                         │
│  │      │  │                                         │
│  └──────┘  │                                         │
│            │                                         │
├────────────┴─────────────────────────────────────────┤
│  (없음 — 풀스크린)                                    │
└──────────────────────────────────────────────────────┘
```

### 좌측 패널 탭 구성

**탭 1: 데이텀 설정**
```
┌─────────────────────────┐
│ [데이텀 유형] ○점 ○선 ○면  │
├─────────────────────────┤
│ 데이텀 입력 방식:         │
│  ○ 좌표 직접 입력         │
│  ○ 지도에서 클릭          │
├─────────────────────────┤
│ 데이텀 포인트 목록:        │
│ ┌─────────────────────┐ │
│ │ #1 35.1°N 129.0°E   │ │
│ │    Error: 3.5 NM     │ │
│ │    [삭제]             │ │
│ ├─────────────────────┤ │
│ │ #2 35.2°N 129.1°E   │ │
│ │    Error: 4.0 NM     │ │
│ │    [삭제]             │ │
│ └─────────────────────┘ │
│                         │
│ [+ 포인트 추가]          │
├─────────────────────────┤
│ [래스터 생성]  [초기화]    │
└─────────────────────────┘
```

**탭 2: 수색자원 (SRU)**
```
┌─────────────────────────┐
│ SRU 목록                 │
│ ┌─────────────────────┐ │
│ │ 선박-1               │ │
│ │  종류: 경비함         │ │
│ │  속도: 6 kts          │ │
│ │  시간: 8 hrs          │ │
│ │  W_corr: 1.2 NM      │ │
│ │  [편집] [삭제]        │ │
│ ├─────────────────────┤ │
│ │ 헬기-1               │ │
│ │  종류: 회전익         │ │
│ │  속도: 60 kts         │ │
│ │  시간: 3 hrs          │ │
│ │  고도: 300m           │ │
│ │  W_corr: 2.5 NM      │ │
│ │  [편집] [삭제]        │ │
│ └─────────────────────┘ │
│                         │
│ [+ SRU 추가]             │
├─────────────────────────┤
│ SRU 추가 폼:             │
│  이름: [________]        │
│  유형: [SHIP ▼]          │
│  수색속도(kts): [___]     │
│  가용시간(hrs): [___]     │
│  고도(m): [___]           │
│  수색대상: [PIW ▼]       │
│  시정(NM): [___]          │
│  피로보정: ☐              │
│  ──── 또는 ────          │
│  W_corr 직접입력: [___]   │
│                          │
│  [등록]  [취소]           │
└──────────────────────────┘
```

**탭 3: 수색계획**
```
┌──────────────────────────┐
│ 수색계획 모드:             │
│ ┌──────┬──────┬────────┐ │
│ │ 수동 │  GA  │Baseline│ │
│ └──────┴──────┴────────┘ │
├──────────────────────────┤
│                          │
│ [수동 모드 선택 시]        │
│  SRU 선택: [선박-1 ▼]    │
│  [지도에서 사각형 그리기]   │
│  ※ 드래그로 위치/크기 조정  │
│  ※ 회전 핸들로 방향 조정   │
│                          │
│ [GA 모드 선택 시]          │
│  GA 파라미터:              │
│   모집단: [100]            │
│   세대수: [500]            │
│   교차율: [0.8]            │
│   변이율: [0.05]           │
│  [최적화 실행]             │
│  진행: ████░░░░ 45%       │
│                          │
│ [Baseline 모드 선택 시]    │
│  ※ 점 데이텀만 지원        │
│  [Baseline 생성]           │
│                          │
├──────────────────────────┤
│ ── 결과 요약 ──           │
│ ┌──────────────────────┐ │
│ │ SRU    POC   POD  POS│ │
│ │ 선박1  0.32  0.78 0.25│ │
│ │ 헬기1  0.45  0.85 0.38│ │
│ │ ─────────────────────│ │
│ │ Total POS:      0.58 │ │
│ │ (중복 셀 보정 적용)    │ │
│ └──────────────────────┘ │
│                          │
│ [결과 내보내기(JSON)]      │
└──────────────────────────┘
```

### 지도 인터랙션

#### 데이텀 표시
- 점 데이텀: 빨간 원형 마커 + 오차 원(반투명 빨간 원, 반경 = E nm)
- 선 데이텀: 빨간 실선(웨이포인트 연결) + 각 웨이포인트에 오차 원
- 면 데이텀: 빨간 반투명 폴리곤 + 각 꼭짓점에 오차 원

#### POC 히트맵
- 래스터 생성 후 지도 위에 반투명 오버레이
- 컬러맵: 파랑(낮음) → 빨강(높음), 투명도 조절 슬라이더
- 50% 확률 영역 경계선 (노란 점선)

#### 수색 구역 (수동 배치)
- SRU별 색상 구분된 회전 사각형
- 드래그: 위치 이동
- 모서리 드래그: 크기 조절
- 회전 핸들: 방향 조절
- **실시간 툴팁** (마우스 호버 또는 드래그 중 표시):
  ```
  ┌─────────────────────┐
  │ 선박-1               │
  │ 면적: 12.5 NM²       │
  │ POC: 0.324           │
  │ C: 1.15              │
  │ POD: 0.683           │
  │ POS: 0.221           │
  └─────────────────────┘
  ```

---

## 5. 핵심 수학 공식

### 5.1 POC 래스터화

#### CEP → 표준편차 변환
```
σ = E × 1852 / 1.17741
```
- E: 총 추정오차 (NM)
- 1852: NM → 미터 변환
- 1.17741: 이변량 정규분포 CEP 계수 (= √(2 ln 2))

#### 샘플링 전략

**점 데이텀**:
- 각 데이텀 점을 그대로 샘플점으로 사용
- 가중치: w = 1 / N (N: 데이텀 점 수)

**선 데이텀**:
- 연속 웨이포인트 쌍의 선분을 따라 등간격 샘플링
- 선분당 샘플 수: n_i = max(1, round(L_i / 50))  (L_i: 선분 길이, 미터)
- 가중치: 전체 경로 길이 대비 해당 선분 비율 → 선분 내 균등 분배
- σ: 양 끝 웨이포인트 오차값 선형 보간

**면 데이텀**:
- 폴리곤 내부를 200m 격자로 래스터 스캔
- 격자점 포함 여부: Ray Casting 알고리즘
- 모든 샘플점에 동일 σ 적용
- 가중치: 균등 (1 / 총 샘플점 수)

#### 래스터 격자 구성
- EPSG:3857 투영, 200m × 200m 해상도
- 범위: 전체 샘플점 바운딩 박스 + 3σ 패딩 (σ 중 최대값 기준)
- Web Mercator 축척 보정: σ' = σ × SF, SF = 1 / cos(φ)

#### 가우시안 확률 계산
```
P(x, y | s) = (w_s / 2πσ'²) × exp(-((x-x_s)² + (y-y_s)²) / 2σ'²)
```

#### 정규화
```
POC(r, c) = P_raw(r, c) / Σ P_raw(r, c)
```

#### 50% 확률 영역 추출
1. POC 값 내림차순 정렬
2. 누적합 0.5 도달 시점의 POC를 임계치로 설정
3. 임계치 이상 셀 → 1.0, 미만 → 0.0 (이진 마스크)
4. 폴리곤 추출 → 50% 확률 영역 경계

### 5.2 POS 계산

#### Coverage Factor
```
C = Z / A
Z = W_corr × V_search × T_endurance  (수색 노력, NM²)
A = 수색 구역 면적 (NM²)
```

#### POD (탐지확률)
```
POD = 1 - exp(-C)
```

#### 보정 탐지폭
```
W_corr = W_0 × f_w × f_v × f_f
```
- W_0: 무보정 탐지폭 (IAMSAR Tab N-4~N-6, 하드코딩 테이블에서 조회)
- f_w: 기상 보정 계수 (Tab N-7)
- f_v: 속도 보정 계수 (Tab N-8)
- f_f: 피로 보정 계수 (0.9 또는 1.0)

#### 다중 고도 POD 시너지
```
POD_combined = 1 - Π(1 - POD_i)
```
- 동일 고도 그룹 내에서는 최대 POD만 대표값
- 서로 다른 고도 그룹 간에만 독립 시행 누적

#### 셀별 POS
```
POS(r, c) = POC(r, c) × POD_combined(r, c)
```

#### 전체 POS
```
totalPOS = Σ POS(r, c)  (POC > 0인 활성 셀에 대해)
```

### 5.3 Web Mercator 보정

```
width_NM = w × cos(φ) / 1852
height_NM = h × cos(φ) / 1852
```

### 5.4 좌표 변환 (EPSG:4326 ↔ EPSG:3857)

```java
// 4326 → 3857
x = lon × 20037508.34 / 180
y = ln(tan((90 + lat) × π / 360)) × 20037508.34 / π

// 3857 → 4326
lon = x × 180 / 20037508.34
lat = atan(exp(y × π / 20037508.34)) × 360 / π - 90
```

---

## 6. IAMSAR 하드코딩 테이블

### 6.1 무보정 탐지폭 (W_0) — IAMSAR Tab N-4, N-5, N-6

DB 없이 Java Map 또는 다차원 배열로 하드코딩한다. 키: (sruType, altitude, visibility, searchObjectType).

#### 회전익 (ROTARY) — Tab N-5 발췌

| 고도(m) | 시정(NM) | PIW | 구명뗏목(1인) | 구명뗏목(4인) | 구명뗏목(6인+) | 동력선(5m) | 동력선(12m) | 범선(10m) | 선박(30m) |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 150 | 1 | 0.1 | 0.2 | 0.3 | 0.4 | 0.7 | 1.3 | 1.0 | 2.5 |
| 150 | 3 | 0.3 | 0.4 | 0.7 | 0.9 | 1.5 | 2.8 | 2.2 | 5.5 |
| 150 | 10 | 0.7 | 0.9 | 1.5 | 2.0 | 3.0 | 5.5 | 4.5 | 10.0 |
| 150 | 15 | 0.8 | 1.1 | 1.8 | 2.3 | 3.5 | 6.2 | 5.2 | 11.5 |
| 150 | 20 | 0.9 | 1.2 | 2.0 | 2.5 | 3.8 | 6.5 | 5.5 | 12.0 |
| 300 | 1 | 0.1 | 0.2 | 0.4 | 0.5 | 0.9 | 1.6 | 1.3 | 3.0 |
| 300 | 3 | 0.3 | 0.5 | 0.9 | 1.1 | 1.8 | 3.4 | 2.7 | 6.5 |
| 300 | 10 | 0.8 | 1.1 | 1.8 | 2.4 | 3.6 | 6.8 | 5.5 | 12.5 |
| 300 | 15 | 0.9 | 1.3 | 2.1 | 2.8 | 4.2 | 7.8 | 6.5 | 14.5 |
| 300 | 20 | 1.0 | 1.4 | 2.3 | 3.0 | 4.5 | 8.2 | 7.0 | 15.5 |
| 600 | 1 | 0.1 | 0.2 | 0.3 | 0.4 | 0.7 | 1.2 | 1.0 | 2.5 |
| 600 | 3 | 0.2 | 0.4 | 0.7 | 0.9 | 1.5 | 2.8 | 2.2 | 5.5 |
| 600 | 10 | 0.6 | 0.9 | 1.5 | 2.0 | 3.2 | 6.0 | 4.8 | 11.0 |
| 600 | 15 | 0.7 | 1.0 | 1.7 | 2.3 | 3.7 | 7.0 | 5.7 | 13.0 |
| 600 | 20 | 0.8 | 1.1 | 1.9 | 2.5 | 4.0 | 7.5 | 6.2 | 14.0 |

#### 고정익 (FIXED) — Tab N-4 발췌

| 고도(m) | 시정(NM) | PIW | 구명뗏목(1인) | 구명뗏목(4인) | 구명뗏목(6인+) | 동력선(5m) | 동력선(12m) | 범선(10m) | 선박(30m) |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 150 | 1 | 0.05 | 0.1 | 0.2 | 0.3 | 0.5 | 1.0 | 0.8 | 2.0 |
| 150 | 3 | 0.2 | 0.3 | 0.5 | 0.7 | 1.2 | 2.2 | 1.8 | 4.5 |
| 150 | 10 | 0.5 | 0.7 | 1.2 | 1.6 | 2.5 | 4.5 | 3.7 | 8.5 |
| 150 | 15 | 0.6 | 0.8 | 1.4 | 1.9 | 2.9 | 5.2 | 4.3 | 9.5 |
| 150 | 20 | 0.7 | 0.9 | 1.6 | 2.1 | 3.2 | 5.5 | 4.6 | 10.0 |
| 300 | 1 | 0.05 | 0.1 | 0.3 | 0.4 | 0.7 | 1.3 | 1.0 | 2.5 |
| 300 | 3 | 0.2 | 0.4 | 0.7 | 0.9 | 1.5 | 2.8 | 2.2 | 5.5 |
| 300 | 10 | 0.6 | 0.9 | 1.5 | 2.0 | 3.0 | 5.8 | 4.6 | 10.5 |
| 300 | 15 | 0.7 | 1.0 | 1.7 | 2.3 | 3.5 | 6.5 | 5.4 | 12.0 |
| 300 | 20 | 0.8 | 1.1 | 1.9 | 2.5 | 3.8 | 7.0 | 5.8 | 13.0 |
| 600 | 1 | 0.05 | 0.1 | 0.2 | 0.3 | 0.5 | 1.0 | 0.8 | 2.0 |
| 600 | 3 | 0.15 | 0.3 | 0.5 | 0.7 | 1.2 | 2.2 | 1.8 | 4.5 |
| 600 | 10 | 0.4 | 0.7 | 1.2 | 1.6 | 2.6 | 5.0 | 4.0 | 9.0 |
| 600 | 15 | 0.5 | 0.8 | 1.4 | 1.9 | 3.0 | 5.8 | 4.7 | 10.5 |
| 600 | 20 | 0.6 | 0.9 | 1.5 | 2.0 | 3.3 | 6.2 | 5.1 | 11.5 |

#### 선박 (SHIP) — Tab N-6 발췌

| 시정(NM) | PIW | 구명뗏목(1인) | 구명뗏목(4인) | 구명뗏목(6인+) | 동력선(5m) | 동력선(12m) | 범선(10m) | 선박(30m) |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | 0.1 | 0.1 | 0.2 | 0.3 | 0.4 | 0.8 | 0.6 | 1.5 |
| 3 | 0.2 | 0.2 | 0.4 | 0.5 | 0.8 | 1.5 | 1.2 | 3.0 |
| 10 | 0.4 | 0.4 | 0.7 | 0.9 | 1.5 | 2.8 | 2.2 | 5.5 |
| 15 | 0.5 | 0.5 | 0.8 | 1.0 | 1.7 | 3.2 | 2.5 | 6.5 |
| 20 | 0.5 | 0.5 | 0.9 | 1.1 | 1.8 | 3.5 | 2.8 | 7.0 |

> **구현 참고**: 테이블에 없는 값은 인접 값 간 선형 보간(linear interpolation)으로 산출한다.
> 정확한 값은 IAMSAR 매뉴얼 원본을 참조하여 교정해야 한다. 위 값은 대략적인 참고치이다.

### 6.2 기상 보정 계수 (f_w) — Tab N-7

기상 보정은 시정(visibility)에 따라 다르며, 여기서는 단순화하여 시정 값 자체를 입력에 반영하므로 f_w = 1.0으로 기본 설정한다. 필요 시 별도 보정 테이블을 추가할 수 있다.

### 6.3 속도 보정 계수 (f_v) — Tab N-8

| 수색속도 / 기준속도 비율 | f_v |
|:---:|:---:|
| 0.5 | 1.15 |
| 0.75 | 1.05 |
| 1.0 | 1.00 |
| 1.25 | 0.95 |
| 1.5 | 0.90 |
| 2.0 | 0.80 |

> 기준속도: 선박 6kts, 회전익 60kts, 고정익 120kts. 비율에 따라 선형 보간.

### 6.4 수색 대상 유형

```java
public enum SearchObjectType {
    PIW("익수자", 0),
    RAFT_1("구명뗏목(1인)", 1),
    RAFT_4("구명뗏목(4인)", 4),
    RAFT_6_PLUS("구명뗏목(6인+)", 6),
    POWERBOAT_5M("동력선(5m)", 5),
    POWERBOAT_12M("동력선(12m)", 12),
    SAILBOAT_10M("범선(10m)", 10),
    SHIP_30M("선박(30m)", 30);
}
```

### 6.5 SRU 유형

```java
public enum SruType {
    SHIP("선박", 0),      // 고도 고정 (해면)
    ROTARY("회전익", 1),   // 고도 선택 가능: 150, 300, 600m
    FIXED("고정익", 2);    // 고도 선택 가능: 150, 300, 600m
}
```

### 6.6 고도 수준

```java
public static final int[] ALTITUDE_LEVELS = {150, 300, 600}; // meters
```

---

## 7. API 명세

### 7.1 데이텀 API

#### POST /api/datum
데이텀 설정 저장 (인메모리)
```json
{
  "type": "POINT",
  "points": [
    { "latitude": 35.1, "longitude": 129.0, "errorNm": 3.5 },
    { "latitude": 35.2, "longitude": 129.1, "errorNm": 4.0 }
  ]
}
```

#### GET /api/datum
현재 데이텀 반환

#### DELETE /api/datum
데이텀 초기화

### 7.2 래스터 API

#### POST /api/raster/generate
POC 래스터 생성. 현재 설정된 데이텀으로부터 POC 래스터를 생성한다.

**Response**:
```json
{
  "rasterInfo": {
    "rows": 150,
    "cols": 200,
    "cellSize": 200,
    "originX": 14360000.0,
    "originY": 4180000.0,
    "totalCells": 30000,
    "activeCells": 8500
  },
  "extent": [14360000.0, 4180000.0, 14400000.0, 4210000.0],
  "contour50": {
    "type": "Feature",
    "geometry": { "type": "Polygon", "coordinates": ["..."] }
  }
}
```

#### GET /api/raster/image
POC 래스터를 PNG 이미지로 반환 (지도 오버레이용).
- Query params: `opacity` (기본: 0.6)
- Response: `image/png`, EPSG:3857 좌표계
- 컬러맵: jet (파랑→초록→노랑→빨강)

#### GET /api/raster/data
POC 래스터 raw 데이터를 JSON으로 반환 (프론트엔드 실시간 계산용).
```json
{
  "rows": 150,
  "cols": 200,
  "cellSize": 200,
  "originX": 14360000.0,
  "originY": 4180000.0,
  "data": [[0.0, 0.0, 0.001], ["..."]]
}
```

#### GET /api/raster/poc?x={x}&amp;y={y}
특정 좌표(EPSG:3857)의 POC 값 조회

### 7.3 SRU API

#### POST /api/sru
SRU 등록
```json
{
  "name": "경비함-1",
  "type": "SHIP",
  "searchSpeed": 6.0,
  "endurance": 8.0,
  "altitude": 0,
  "searchObjectType": "PIW",
  "visibility": 10,
  "fatigue": false,
  "correctedSweepWidth": null
}
```
- `correctedSweepWidth`가 null이면 테이블에서 W_0 조회 → f_v, f_f 적용하여 자동 계산
- `correctedSweepWidth`가 값이 있으면 그 값을 직접 사용

#### GET /api/sru
전체 SRU 목록

#### PUT /api/sru/{id}
SRU 수정

#### DELETE /api/sru/{id}
SRU 삭제

### 7.4 수색계획 API

#### POST /api/search-plan/manual/evaluate
수동 배치 평가 (드래그 중 실시간 호출)
```json
{
  "areas": [
    {
      "sruId": "sru-1",
      "centerX": 14365000.0,
      "centerY": 4185000.0,
      "width": 5000.0,
      "height": 4000.0,
      "rotation": 30.0
    }
  ]
}
```

**Response**:
```json
{
  "totalPOS": 0.583,
  "sruResults": [
    {
      "sruId": "sru-1",
      "sruName": "경비함-1",
      "areaNm2": 12.5,
      "poc": 0.324,
      "coverageFactor": 1.15,
      "pod": 0.683,
      "pos": 0.221
    }
  ]
}
```

#### POST /api/search-plan/ga/optimize
GA 최적화 실행 (비동기, SSE로 진행 상태 전송)
```json
{
  "config": {
    "populationSize": 100,
    "maxGenerations": 500,
    "crossoverRate": 0.8,
    "mutationRate": 0.05,
    "tournamentSize": 5,
    "elitismCount": 2,
    "convergenceGenerations": 50
  }
}
```

**SSE 이벤트 스트림** (GET /api/search-plan/ga/optimize/stream):
```
event: progress
data: {"generation": 45, "bestFitness": 0.412, "avgFitness": 0.285}

event: progress
data: {"generation": 46, "bestFitness": 0.415, "avgFitness": 0.290}

event: complete
data: {"totalPOS": 0.583, "generations": 234, "sruResults": ["..."], "areas": ["..."]}
```

#### POST /api/search-plan/baseline/generate
IAMSAR Baseline 배치 생성 (점 데이텀만 지원)

**Response**:
```json
{
  "totalPOS": 0.425,
  "sruResults": ["..."],
  "areas": ["..."],
  "method": "IAMSAR_BASELINE",
  "description": "L-7 워크시트 기반 타일링 배치"
}
```

---

## 8. 주요 서비스 구현 상세

### 8.1 RasterService — POC 래스터 생성

```
입력: DatumSet (type + List&lt;DatumPoint&gt;)
출력: PocRaster (double[][] + 메타데이터) + PNG 이미지 + 50% 영역 GeoJSON
```

**처리 흐름**:
1. 데이텀 유형에 따라 SamplingStrategy 선택 (점/선/면)
2. 샘플점 생성: List&lt;SamplePoint&gt; (x_3857, y_3857, σ, weight)
3. 바운딩 박스 계산 + 3σ 패딩 (σ 중 최대값 기준)
4. 200m 격자 생성
5. 가우시안 LUT 생성 (z = r/σ, 0 ~ 3σ 범위, 1000단계)
6. 각 샘플점에 대해 주변 격자 셀에 확률 기여 누적 (병렬 처리 가능)
7. 정규화 (총합 = 1.0)
8. 50% 확률 영역 추출
9. PNG 이미지 생성 (jet 컬러맵)

**가우시안 LUT**:
```java
double[] gaussianLut = new double[1001];
double maxZ = 3.0; // 3σ 범위
for (int i = 0; i &lt;= 1000; i++) {
    double z = maxZ * i / 1000.0;
    gaussianLut[i] = Math.exp(-z * z / 2.0);
}
```

**PNG 생성**: Java `BufferedImage`로 직접 생성. 각 셀의 POC 값을 0~maxPoc로 매핑하여 jet 컬러맵 적용. POC = 0인 셀은 완전 투명(alpha = 0).

**Jet 컬러맵 (간이 구현)**:
```java
// t: 0.0 ~ 1.0 정규화 값
Color jetColor(double t) {
    double r = clamp(1.5 - Math.abs(t - 0.75) * 4, 0, 1);
    double g = clamp(1.5 - Math.abs(t - 0.5) * 4, 0, 1);
    double b = clamp(1.5 - Math.abs(t - 0.25) * 4, 0, 1);
    return new Color((int)(r*255), (int)(g*255), (int)(b*255));
}
```

### 8.2 GaOptimizer — GA 최적화 엔진

**알고리즘 흐름**:
```
1. 초기 모집단 생성
   - 20%: POC 핫스팟 시딩
   - 80%: 무작위
2. 반복 (최대 maxGenerations):
   a. 적합도 평가 (FitnessEvaluator)
   b. 엘리트 보존 (상위 2개)
   c. 선택 (Tournament, k=5)
   d. 교차 (SBX, η_c=20, rate=0.8)
   e. 변이 (Gaussian, rate=0.05)
   f. 중첩 수리 (OverlapRepairer)
   g. 수렴 판정 (50세대 연속 개선 없음)
3. 최적 개체 반환
```

**유전자 초기화 범위**:
- centerX/Y: 래스터 범위 내
- width/height: 기준 면적(Z/SRU 기준)의 0.5배~3.0배에서 산출
- rotation: 0~360°
- altitudeIndex: SRU 유형에 따라 (SHIP → 0 고정, 항공기 → 0~2)

**적합도 함수**:
```
fitness = totalPOS - penalty_overlap - penalty_soft

penalty_overlap = 10⁶ × (1 + overlap_ratio)  (동일 고도 중첩 시)
penalty_coverage = 10.0 × |C - clamp(C, 0.5, 3.0)|
penalty_aspect = 10.0 × |r - clamp(r, 0.3, 3.0)|
```

**SBX 교차** (연속 변수, η_c = 20):
```
u = random()
if u &lt;= 0.5:
    β = (2u)^(1/(η+1))
else:
    β = (1/(2(1-u)))^(1/(η+1))
child1 = 0.5 × ((1+β)×parent1 + (1-β)×parent2)
child2 = 0.5 × ((1-β)×parent1 + (1+β)×parent2)
```

**가우시안 변이** (각 유전자의 각 변수에 5% 확률):
- center: ±10% 래스터 범위 (nextGaussian × range × 0.1)
- size: ×(1 + nextGaussian × 0.2), clamp to [0.5×base, 3.0×base]
- rotation: ±15° (nextGaussian × 15)
- altitude: 균일 무작위 재선택 (항공기만)

**중첩 수리** (OverlapRepairer):
- 동일 고도 SRU 간 200m 안전 버퍼 포함 확대 사각형으로 검사
- 중첩 시 두 중심을 반대 방향으로 밀어내기 (중첩 깊이의 50%씩)
- 최대 50회 외부 × 10회 내부 반복
- 수리 후 중심은 래스터 범위 내로 클램핑

### 8.3 BaselinePlanService — IAMSAR Baseline 배치

**지원 케이스**: 점 데이텀만 (정사각형 배치)

**알고리즘**:
1. POC 가중 중심 계산 (래스터에서 Σ(POC × 좌표) / Σ(POC))
2. 각 SRU의 Z (수색 노력) 계산: Z = W_corr × V × T
3. 최적 구역 면적 A 결정: A = Z (Coverage Factor C ≈ 1.0 목표)
4. 정사각형 구역: side = √A
5. POC 가중 중심에서 겹치지 않게 타일링 배치
   - 첫 번째 SRU: 가중 중심에 배치
   - 두 번째 이후: 인접 타일링 (좌/우/상/하 순)
6. 수색 방향(회전각): 0° 고정
7. POS 계산

### 8.4 ManualPlanService — 수동 배치 평가

사용자가 지도에서 그린 사각형의 POS를 계산한다.

**처리 흐름**:
1. 각 SRU의 수색 구역(회전 사각형) 수신
2. 각 SRU에 대해:
   a. 구역 내 POC 셀 합산 → SRU별 POC
   b. 구역 면적(NM²) 계산 (Web Mercator cos(φ) 보정)
   c. Coverage Factor C = Z / A
   d. POD = 1 - exp(-C)
   e. SRU별 POS = POC × POD
3. 전체 POS: 다중 고도 시너지 적용 (셀 단위 계산)

### 8.5 PosCalculator — 공통 POS 계산

ManualPlanService, FitnessEvaluator, BaselinePlanService가 공통 사용.

```java
public class PosCalculator {
    /** 단일 SRU의 POD */
    public double calculatePod(double coverageFactor) {
        return 1.0 - Math.exp(-coverageFactor);
    }

    /** Coverage Factor */
    public double calculateCoverageFactor(Sru sru, double areaNm2) {
        double Z = sru.getCorrectedSweepWidth() * sru.getSearchSpeed() * sru.getEndurance();
        return Z / areaNm2;
    }

    /** 회전 사각형 내부 POC 합산 */
    public double sumPocInRect(PocRaster raster, SearchArea area) {
        // AABB 먼저 계산하여 후보 셀 축소
        // 후보 셀 중 회전 사각형 내부 판정 (point-in-rotated-rect)
        // 내부 셀의 POC 합산
    }

    /** 전체 POS (다중 고도 시너지 포함) */
    public double calculateTotalPos(PocRaster raster, List&lt;SearchArea&gt; areas, List&lt;Sru&gt; srus) {
        // 1. 각 활성 셀(POC &gt; 0)에 대해
        // 2. 해당 셀을 커버하는 SRU들의 고도별 POD 계산
        // 3. 동일 고도 그룹 → 최대 POD만 채택
        // 4. 다른 고도 그룹 간 → POD_combined = 1 - Π(1 - POD_i)
        // 5. POS(cell) = POC(cell) × POD_combined(cell)
        // 6. totalPOS = Σ POS(cell)
    }

    /** 회전 사각형 면적 (NM², Web Mercator 보정) */
    public double calculateAreaNm2(SearchArea area, double latDeg) {
        double cosFactor = Math.cos(Math.toRadians(latDeg));
        double widthNm = area.getWidth() * cosFactor / 1852.0;
        double heightNm = area.getHeight() * cosFactor / 1852.0;
        return widthNm * heightNm;
    }
}
```

---

## 9. 프론트엔드 구현 상세

### 9.1 지도 초기화 (mapInit.js)

```javascript
const map = new ol.Map({
    target: 'mapContainer',
    view: new ol.View({
        projection: 'EPSG:3857',
        center: ol.proj.transform([128.5, 35.5], 'EPSG:4326', 'EPSG:3857'),
        zoom: 9,
        maxZoom: 18
    }),
    controls: ol.control.defaults.defaults({ zoom: true, rotate: false })
});

// OSM 베이스 레이어
const osmLayer = new ol.layer.Tile({ source: new ol.source.OSM() });
map.addLayer(osmLayer);
```

### 9.2 데이텀 레이어 (datumLayer.js)

```javascript
// Vector 레이어: 데이텀 포인트 + 오차 원
const datumSource = new ol.source.Vector();
const datumLayer = new ol.layer.Vector({
    source: datumSource,
    style: function(feature) {
        const type = feature.get('featureType');
        if (type === 'errorCircle') {
            return new ol.style.Style({
                stroke: new ol.style.Stroke({ color: 'rgba(255,0,0,0.5)', width: 1 }),
                fill: new ol.style.Fill({ color: 'rgba(255,0,0,0.1)' })
            });
        }
        // 점 마커, 선, 면 스타일 분기
    }
});
```

- 지도 클릭 이벤트로 데이텀 포인트 추가
- 각 포인트에 오차 원 표시 (ol.geom.Circle, 반경 = errorNm × 1852 / cos(φ))
- 선 데이텀: ol.geom.LineString 으로 웨이포인트 연결
- 면 데이텀: ol.geom.Polygon 으로 꼭짓점 연결

### 9.3 POC 히트맵 레이어 (pocLayer.js)

```javascript
// 서버에서 받은 PNG를 ImageLayer로 오버레이
function showPocHeatmap(extent) {
    const pocLayer = new ol.layer.Image({
        source: new ol.source.ImageStatic({
            url: '/api/raster/image',
            imageExtent: extent,  // [minX, minY, maxX, maxY] EPSG:3857
            projection: 'EPSG:3857'
        }),
        opacity: 0.6,
        zIndex: 10
    });
    map.addLayer(pocLayer);
}

// 50% 확률 영역 경계선
function showContour50(geojson) {
    const format = new ol.format.GeoJSON();
    const features = format.readFeatures(geojson, {
        featureProjection: 'EPSG:3857'
    });
    contourSource.addFeatures(features);
}

// 투명도 슬라이더
document.getElementById('opacitySlider').addEventListener('input', function(e) {
    pocLayer.setOpacity(parseFloat(e.target.value));
});
```

### 9.4 수색 구역 — 수동 배치 (searchAreaLayer.js)

**핵심 인터랙션**: 회전 가능한 사각형을 지도 위에서 드래그로 생성/수정.

```javascript
// 수색 구역 그리기 모드
function startDrawSearchArea(sruId) {
    const draw = new ol.interaction.Draw({
        source: searchAreaSource,
        type: 'Circle',
        geometryFunction: ol.interaction.Draw.createBox()
    });

    draw.on('drawend', function(event) {
        const feature = event.feature;
        feature.set('sruId', sruId);
        feature.set('rotation', 0);
        enableModify(feature);
        evaluateSearchAreas();
    });
    map.addInteraction(draw);
}

// Modify: 위치 이동, 크기 조절
const modify = new ol.interaction.Modify({ source: searchAreaSource });
modify.on('modifyend', function() {
    evaluateSearchAreas();
});

// 회전: Shift+드래그 또는 별도 회전 UI
// → feature의 geometry를 중심점 기준으로 affine 회전 적용
```

**실시간 툴팁** (수색 구역 호버 시):
```javascript
// 래스터 데이터를 프론트에 캐시 → 드래그 중 실시간 계산
let pocRasterCache = null;

function calculateTooltip(feature, sru) {
    const geom = feature.getGeometry();
    const extent = geom.getExtent();
    const center = ol.extent.getCenter(extent);
    const [lon, lat] = ol.proj.transform(center, 'EPSG:3857', 'EPSG:4326');

    // 면적 계산 (NM²) — geom 꼭짓점에서 실제 거리 계산
    const coords = geom.getCoordinates()[0];
    const w = ol.sphere.getDistance(
        ol.proj.transform(coords[0], 'EPSG:3857', 'EPSG:4326'),
        ol.proj.transform(coords[1], 'EPSG:3857', 'EPSG:4326')
    ) / 1852;
    const h = ol.sphere.getDistance(
        ol.proj.transform(coords[1], 'EPSG:3857', 'EPSG:4326'),
        ol.proj.transform(coords[2], 'EPSG:3857', 'EPSG:4326')
    ) / 1852;
    const areaNm2 = w * h;

    // Coverage Factor &amp; POD
    const Z = sru.correctedSweepWidth * sru.searchSpeed * sru.endurance;
    const C = Z / areaNm2;
    const POD = 1 - Math.exp(-C);

    // POC (래스터 캐시에서 계산)
    const POC = sumPocInFeature(pocRasterCache, feature);
    const POS = POC * POD;

    return { areaNm2: areaNm2.toFixed(1), POC: POC.toFixed(3), C: C.toFixed(2), POD: POD.toFixed(3), POS: POS.toFixed(3) };
}

// ol.Overlay로 툴팁 표시
const tooltipOverlay = new ol.Overlay({
    element: document.getElementById('searchAreaTooltip'),
    positioning: 'bottom-center',
    offset: [0, -10]
});
map.addOverlay(tooltipOverlay);
```

> **구현 권장**: POC 래스터 데이터를 프론트엔드에도 전달하여 (/api/raster/data), 드래그 중 실시간 계산을 프론트에서 수행하는 것이 UX에 유리하다. 래스터 크기가 작으므로 (수만 셀) 브라우저에서 충분히 처리 가능하다.

### 9.5 GA 최적화 UI (gaPlan.js)

```javascript
// SSE로 GA 진행 상황 수신
function startGaOptimization(config) {
    // POST로 최적화 시작
    $.ajax({
        url: '/api/search-plan/ga/optimize',
        type: 'POST',
        contentType: 'application/json; charset=utf-8',
        data: JSON.stringify({ config: config }),
        success: function(result) {
            // 최적화 ID 수신 후 SSE 연결
            const eventSource = new EventSource('/api/search-plan/ga/optimize/stream?id=' + result.optimizationId);

            eventSource.addEventListener('progress', function(e) {
                const data = JSON.parse(e.data);
                updateProgressBar(data.generation, config.maxGenerations);
            });

            eventSource.addEventListener('complete', function(e) {
                const result = JSON.parse(e.data);
                displayGaResult(result);
                eventSource.close();
            });

            eventSource.addEventListener('error', function() {
                eventSource.close();
            });
        }
    });
}

function displayGaResult(result) {
    // 수색 구역을 지도에 표시
    result.areas.forEach(function(area) {
        addSearchAreaFeature(area);
    });
    // 결과 테이블 갱신
    updateResultTable(result.sruResults, result.totalPOS);
}
```

### 9.6 SRU별 색상

```javascript
const SRU_COLORS = [
    '#2196F3', // 파랑
    '#4CAF50', // 초록
    '#FF9800', // 주황
    '#9C27B0', // 보라
    '#F44336', // 빨강
    '#00BCD4', // 청록
    '#795548', // 갈색
    '#607D8B', // 회색
];
```

### 9.7 스타일 가이드

```css
/* 색상 팔레트 */
:root {
    --primary: #05205F;
    --secondary: #0B4D93;
    --accent: #DFE9F6;
    --bg-light: #F7F8FC;
    --border: #B2B7C7;
    --text: #222222;
    --text-sub: #666666;
    --danger: #ED183C;
}

/* 폰트 */
body {
    font-family: 'Pretendard', -apple-system, sans-serif;
    font-size: 14px;
}

/* 입력 필드 */
input, select {
    border: 1px solid var(--border);
    padding: 5px 10px;
    border-radius: 4px;
}

/* 버튼 */
.btn-primary { background: var(--secondary); color: white; border: none; padding: 6px 16px; border-radius: 4px; cursor: pointer; }
.btn-secondary { background: var(--accent); color: var(--secondary); border: none; padding: 6px 16px; border-radius: 4px; cursor: pointer; }
.btn-danger { background: var(--danger); color: white; border: none; padding: 6px 16px; border-radius: 4px; cursor: pointer; }

/* 좌측 패널 */
.left-panel {
    position: absolute;
    left: 0; top: 50px;
    width: 360px;
    height: calc(100% - 50px);
    background: white;
    z-index: 10;
    overflow-y: auto;
    border-right: 1px solid var(--border);
}

/* 탭 */
.tab-buttons { display: flex; border-bottom: 2px solid var(--border); }
.tab-button { flex: 1; padding: 10px; text-align: center; cursor: pointer; border: none; background: none; }
.tab-button.active { border-bottom: 2px solid var(--secondary); color: var(--secondary); font-weight: bold; }

/* 지도 */
.map-container {
    position: absolute;
    left: 360px; top: 50px;
    right: 0; bottom: 0;
}
```

---

## 10. 빌드 설정

### build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.0.5'
    id 'io.spring.dependency-management' version '1.1.0'
}

group = 'paper.validation'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '17'

repositories {
    mavenCentral()
    maven { url 'https://repo.osgeo.org/repository/release/' }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    implementation 'nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect'

    // GIS
    implementation 'org.geotools:gt-main:27.2'
    implementation 'org.geotools:gt-geotiff:27.2'
    implementation 'org.geotools:gt-geojson:27.2'
    implementation 'org.locationtech.jts:jts-core:1.20.0'

    // Utilities
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    implementation 'org.modelmapper:modelmapper:3.1.1'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### application.yml

```yaml
server:
  port: 8080

spring:
  thymeleaf:
    cache: false
    prefix: classpath:/templates/
    suffix: .html

logging:
  level:
    paper.validation: DEBUG
```

---

## 11. 구현 순서 권장

### Phase 1: 기반 구조
1. Spring Boot 프로젝트 생성, Gradle 설정
2. Thymeleaf 레이아웃 (좌측 패널 + 우측 지도)
3. OpenLayers 지도 초기화 (OSM 베이스맵)
4. 좌표 변환 유틸리티 (CoordinateConverter)

### Phase 2: 데이텀 + 래스터
5. 데이텀 입력 UI (점/선/면 전환, 좌표 입력/지도 클릭)
6. 데이텀 레이어 (마커 + 오차 원)
7. DatumService (인메모리 저장)
8. RasterService (POC 래스터화 — 가우시안 커널)
9. POC 히트맵 지도 오버레이 + 50% 영역 경계선

### Phase 3: SRU 관리
10. SRU 등록/수정/삭제 UI
11. SruService (인메모리)
12. SweepWidthTable (하드코딩 조회 + 보간)

### Phase 4: 수동 배치
13. 수색 구역 드로잉 인터랙션 (회전 가능 사각형)
14. 실시간 POS 계산 + 툴팁
15. PosCalculator (공통 POS/POD 계산)
16. 결과 요약 패널

### Phase 5: GA 최적화
17. GaOptimizer (GA 엔진)
18. FitnessEvaluator, 유전 연산자들
19. SSE 진행 상태 전송
20. GA 결과 지도 표시

### Phase 6: IAMSAR Baseline
21. BaselinePlanService (점 데이텀 전용)
22. Baseline 결과 표시
23. 3가지 방식 비교 UI

### Phase 7: 마무리
24. 결과 JSON 내보내기
25. POC 투명도 슬라이더
26. 수렴 곡선 차트 (GA 세대별 fitness)
27. UI 폴리싱

---

## 12. 참고 — 기존 AISAR 프로젝트 코드

새 프로젝트는 독립적이지만, 기존 AISAR 프로젝트에 동일 알고리즘의 구현체가 있으므로 참고할 수 있다:

| 기능 | 기존 코드 위치 |
|------|---------------|
| POC 래스터화 | `image-generator/.../iamsar/strategy/DatumStrategy.java` |
| 점 데이텀 샘플링 | `image-generator/.../iamsar/strategy/PointStrategy.java` |
| 선 데이텀 샘플링 | `image-generator/.../iamsar/strategy/LineStrategy.java` |
| 면 데이텀 샘플링 | `image-generator/.../iamsar/strategy/AreaStrategy.java` |
| GA 엔진 | `plan/.../ga/GaOptimizer.java` |
| 6D 유전자 | `plan/.../ga/domain/SruGene.java` |
| 적합도 평가 | `plan/.../ga/operator/FitnessEvaluator.java` |
| SBX 교차 | `plan/.../ga/operator/CrossoverOperator.java` |
| 가우시안 변이 | `plan/.../ga/operator/MutationOperator.java` |
| 중첩 수리 | `plan/.../ga/GaOptimizer.java` (repairOverlaps) |
| PocRaster 도메인 | `plan/.../ga/domain/PocRaster.java` |
| 회전 사각형 기하 | `plan/.../ga/util/RectangleGeometry.java` |
| 좌표 변환 | `plan/.../ga/util/CoordinateConverter.java` |
| 탐지폭 테이블 | `user/.../searchplan/SweepWidthServiceImpl.java` |
| 속도 보정 | `user/.../searchplan/VelocityFactorServiceImpl.java` |
| Probability 계산 | `plan/.../domain/probability/Probability.java` |
| GA 설정 | `plan/.../ga/GaConfig.java` |

> 이 코드들을 직접 복사하지 말고, 논문의 수식과 알고리즘 명세를 기준으로 새로 구현하되 기존 코드를 참고 자료로 활용한다.

---

## 13. 주의사항

### 프론트엔드
- `innerHTML` 사용 금지 → `textContent` 또는 Handlebars 사용
- ES6 Modules에서 import 경로에 `.js` 확장자 필수
- jQuery, ol, turf 등 외부 라이브러리는 전역 변수로 접근
- 세미콜론 필수, 싱글 쿼트 사용

### 백엔드
- DB 없음 → 모든 상태를 인메모리 Map/List로 관리 (ConcurrentHashMap 등)
- GA 최적화는 비동기 실행 (CompletableFuture 또는 @Async)
- SSE(Server-Sent Events)로 GA 진행 상태 스트리밍
- 래스터 데이터 크기: 200m 격자이므로 일반적으로 수백×수백 셀 규모. 메모리 이슈 없음.

### 좌표계
- 서버 내부 계산: EPSG:3857 (Web Mercator) — 래스터, GA
- API 입출력: EPSG:4326 (WGS84) — 위경도
- 프론트엔드 지도: EPSG:3857 (OpenLayers 기본)
- 면적 계산 시 Web Mercator 왜곡 보정 필수: × cos(φ)

### 보안
- 외부 입력 검증 (좌표 범위, 숫자 타입)
- API 응답에 스택트레이스 노출 금지
- eval(), new Function() 금지
