# 장소 데이터 연동 — TourAPI · 카카오 로컬

> 최종 수정 2026-09-19 · 근거: PLAN-06·07·08 · ADR-004
>
> **이 문서는 외부 API 두 개의 연동 규격을 담습니다.** `places`·`regions` 테이블을
> 채우는 배치가 실제로 무엇을 호출하는지는 `evergardenapi.yaml`에 안 나옵니다 —
> 그건 우리 서버가 앱에게 제공하는 API만 정의하고, 우리가 내부적으로 외부 API를
> 어떻게 부르는지는 구현 디테일이라서입니다.
>
> TourAPI 원본 명세서(`한국관광공사_개방데이터_활용매뉴얼(국문)_v4.4.docx`)는
> 프로젝트에 보관하지 않기로 해서, 실제 구현에 필요한 만큼만 여기 옮겨 적습니다.

---

## 1. 전체 그림

```
1. ldongCode2(TourAPI)로 법정동 시/도·시군구 코드·이름을 받는다      → Region 뼈대
2. 카카오 로컬 API로 그 지역명 하나하나의 좌표를 받는다              → Region 완성
3. areaBasedList2(TourAPI)로 지역별 관광지를 받아 초기 시딩한다      → Place
4. 매일 areaBasedSyncList2(TourAPI)로 변경분만 받아 갱신한다         → Place 동기화
5. getPlace(PLAN-07)가 처음 불릴 때만 detailCommon2·detailIntro2·
   detailImage2를 실시간으로 불러 상세를 채운다(ADR-061)            → Place 상세
```

**좌표계는 둘 다 WGS84다.** TourAPI의 `mapx`/`mapy`, 카카오의 `x`/`y`를
`lat`/`lng`에 변환 없이 그대로 넣으면 된다.

---

## 2. TourAPI (한국관광공사 국문 관광정보 서비스)

- 서비스 그룹: `KorService2` (제공기관 코드 `B551011`)
- 베이스 URL: `https://apis.data.go.kr/B551011/KorService2`
- 공통 필수 파라미터: `serviceKey`(Decoding 인증키) · `MobileOS` · `MobileApp` · `numOfRows` · `pageNo`
- JSON으로 받으려면 `_type=json` (기본은 XML)

### 2.1 법정동 코드 조회 — `ldongCode2`

`Region` 테이블의 시/도·시군구 코드·이름 뼈대를 채운다. **좌표는 안 준다.**

**2단계로 호출해야 한다:**

| 호출 | 파라미터 | 결과 |
|---|---|---|
| ① 시/도 목록 | `lDongRegnCd` 생략 | 시/도 17개 (`code`/`name` 페어) |
| ② 시군구 목록 | `lDongRegnCd=<시도코드>` + `lDongListYn=Y` | 그 시/도의 시군구 목록 (`lDongRegnCd`/`lDongRegnNm`/`lDongSignguCd`/`lDongSignguNm`) |

요청 파라미터:

| 이름 | 필수 | 설명 |
|---|---|---|
| `lDongRegnCd` | 선택 | 법정동 시도코드. 없으면 전체 시도목록 호출 |
| `lDongListYn` | 선택 | `N`(기본, 코드조회) / `Y`(전체목록조회) |

응답 필드 (`lDongListYn`에 따라 둘 중 하나만 나온다):

| 모드 | 필드 |
|---|---|
| `N` | `code`, `name`, `rnum` |
| `Y` | `lDongRegnCd`, `lDongRegnNm`, `lDongSignguCd`, `lDongSignguNm`, `rnum` |

예제: `GET .../ldongCode2?...&lDongRegnCd=11&lDongListYn=Y` → 서울(`lDongRegnCd=11`)의 시군구 전체 목록, 각 항목에 `lDongSignguCd`/`lDongSignguNm` 포함.

### 2.2 지역기반 관광정보 조회 — `areaBasedList2`

초기 `Place` 시딩용. 지역·타입으로 필터링해 목록을 받는다.

요청 파라미터 (주요):

| 이름 | 필수 | 설명 |
|---|---|---|
| `lDongRegnCd` / `lDongSignguCd` | 선택 | 지역 필터 (`ldongCode2` 결과값) |
| `contentTypeId` | 선택 | 12=관광지, 14=문화시설, 15=축제공연행사, 25=여행코스, 28=레포츠, 32=숙박, 38=쇼핑, 39=음식점 |
| `arrange` | 선택 | `A`=제목순, `C`=수정일순, `D`=생성일순 |
| `modifiedtime` | 선택 | 콘텐츠 수정일(`YYYYMMDD`) 필터 |

응답 필드 → `Place` 매핑:

| TourAPI 필드 | `Place` 필드 | 비고 |
|---|---|---|
| `contentid` | `contentId` | |
| `contenttypeid` | `contentTypeId` | |
| `title` | `title` | |
| `addr1` | `addr` | |
| `tel` | `tel` | |
| `mapx` | `lng` | **경도** — WGS84 |
| `mapy` | `lat` | **위도** — WGS84 |
| `firstimage2` | `thumbnailUrl` | 썸네일(약 150×100). `firstimage`는 원본(약 500×333) |
| `modifiedtime` | — | 다음 증분 동기화 기준값으로 참고 |
| `lDongRegnCd`/`lDongSignguCd` | `region` | `Region`과 조인하는 키 |

`useTime`/`restDate`(이용시간·휴무일)는 이 오퍼레이션이 아니라 **소개 정보 조회(`detailIntro2`)**에서
나온다 — 초기 시딩·일일 동기화(`areaBasedList2`/`areaBasedSyncList2`)에서는 안 채우고, `getPlace`
(PLAN-07)가 처음 조회될 때 실시간으로 따로 불러 채운다(ADR-061). 5절에 이미 활용신청돼 있다.

### 2.3 관광정보 동기화 목록 조회 — `areaBasedSyncList2`

매일 배치로 "어제 이후 바뀐 것만" 받는 증분 동기화용.

요청 파라미터 (`areaBasedList2`와 거의 동일, 추가되는 것만):

| 이름 | 필수 | 설명 |
|---|---|---|
| `modifiedtime` | 선택 | `YYYYMMDD` — **이 날짜 이후 바뀐 것만** 반환 |
| `showflag` | 선택 | `1`=표출, `0`=비표출(콘텐츠가 내려간 경우 — DB에서 지우거나 숨겨야 함) |
| `oldContentid` | 선택 | 콘텐츠 ID가 바뀐 경우 이전 ID로 조회 (드문 케이스) |

응답 필드는 `areaBasedList2`와 동일 + `showflag`.

**배치 로직 요지**:
```
매일:
  areaBasedSyncList2(modifiedtime=어제)로 변경분 전부 페이지네이션해서 받기
  각 항목:
    showflag == "1" → upsert (contentid로 기존 Place 찾아 sync(), 없으면 새로 생성)
    showflag == "0" → 숨김 처리 또는 삭제
```

**실제 구현(`PlaceIncrementalSyncService`, 2026-09-18)에서는 `showflag == "0"`을 그냥
건너뛴다** — 숨김 처리도 삭제도 하지 않는다. 이유:

- `places` 테이블에 "숨김" 상태를 표현할 컬럼이 없다.
- 하드 삭제는 위험하다 — `trip_places.place_id`가 `places(id)`를 참조하는데
  `ON DELETE` 절이 없어(기본 `NO ACTION`), 이미 누군가의 일정에 담긴 장소를 지우려 하면
  외래키 제약 위반으로 그 자리에서 예외가 난다.

지역·타입으로 나누지도 않는다 — 하루치 변경분은 전국을 통틀어도 수백 건 수준이라
(실전 확인: 일주일치 변경분이 201건), 시딩 때처럼 시/도×타입으로 쪼갤 필요가 없다.
`modifiedtime` 하나만으로 전국·전체 타입을 페이지네이션해서 받는다.

**후속 작업으로 남겨둔 것**: `showflag == "0"`을 실제로 반영하려면 `places`에 노출 여부
컬럼을 추가하는 마이그레이션과, `searchPlaces`·`listNearbyPlaces`가 그 컬럼을 걸러 읽도록
고치는 작업이 같이 필요하다.

### 2.4 상세조회 3종 — `getPlace`(PLAN-07)가 처음 조회될 때만 부른다(ADR-061)

초기 시딩·일일 동기화와 달리 배치가 아니라 **`getPlace` 요청이 들어왔을 때, 그 장소의
`detail_synced_at`이 아직 `null`이면 딱 한 번만** 부른다. 받은 값은 `places`에
영구 저장하고 다시는 안 묻는다 — "짧은 TTL 캐시"가 아니라 DB 자체가 캐시다.

#### 공통정보조회 — `detailCommon2`

`overview`(소개글)용.

| 이름 | 필수 | 설명 |
|---|---|---|
| `contentId` | 필수 | 콘텐츠랩 contentid |

**`contentId` 외의 파라미터를 전부 거부한다** — `contentTypeId`·`overviewYN`·
`defaultYN` 같은 흔히 붙이는 옵션을 넣으면 요청 자체가 거부된다(실전 확인).
다른 조회 오퍼레이션과 습관적으로 파라미터를 맞추면 안 된다.

응답 필드 중 쓰는 것: `contentid`, `overview`.

#### 소개정보조회 — `detailIntro2`

`useTime`/`restDate`용. `contentId`뿐 아니라 **`contentTypeId`도 같이 보내야** 한다.

| 이름 | 필수 | 설명 |
|---|---|---|
| `contentId` | 필수 | 콘텐츠랩 contentid |
| `contentTypeId` | 필수 | 관광지=12, 문화시설=14, 음식점=39 등 |

이용시간·휴무일 필드명이 콘텐츠타입마다 다르다:

| 콘텐츠타입 | 이용시간 필드 | 휴무일 필드 |
|---|---|---|
| 관광지(12) | `usetime` | `restdate` |
| 문화시설(14) | `usetimeculture` | `restdateculture` |
| 음식점(39) | `opentimefood` | `restdatefood` |

**이 세 타입만 지원한다.** 실측(다른 팀의 실제 구현·PR — team-chaerok/chaerok-be#107,
meomul-kyung/back#16) 기준 관광지·문화시설·음식점만 값이 신뢰할 수 있게 채워져
있었다. 나머지 다섯 타입(축제행사=15, 여행코스=25, 레포츠=28, 숙박=32, 쇼핑=38)은
"이용시간·휴무일"과 다른 개념(행사 기간, 소요 시간, 체크인 시간 등)을 쓰거나
실측상 필드가 비어 있어 억지로 끼워 맞추지 않는다 — `useTime`/`restDate`가 `null`로
남는다.

#### 관광사진정보조회 — `detailImage2`

`imageUrls`용.

| 이름 | 필수 | 설명 |
|---|---|---|
| `contentId` | 필수 | 콘텐츠랩 contentid |

응답 필드 중 쓰는 것: `contentid`, `originimgurl`(원본 이미지 URL). 한 콘텐츠에
여러 장이 올 수 있어 배열로 받아 그대로 저장한다.

---

## 3. 카카오 로컬 API — 주소로 좌표 변환

`ldongCode2`가 못 주는 `Region.centerLat`/`centerLng`를 채우는 용도. **최초 1회, 지역명 약 267개(시/도 17 + 시군구 약 250)에 대해서만 돌리는 시딩 작업**이고, 반복 동기화 대상이 아니다.

- 엔드포인트: `GET https://dapi.kakao.com/v2/local/search/address.json`
- 인증 헤더: `Authorization: KakaoAK {REST_API_KEY}`
- 요청 파라미터: `query`(필수, 검색할 주소/지역명 — 예: `"서울특별시 종로구"`)
- 응답: `x`(경도), `y`(위도) — **WGS84**, `documents[0]`에서 꺼내면 됨

주의: 시/도 단위(`"서울특별시"`만)로 검색하면 결과가 안 나오거나 부정확할 수 있어, 시군구는 `"시/도명 + 시군구명"`을 합쳐서 검색하는 게 안전하다. 시/도 자체의 대표 좌표는 그 시/도의 도청 소재지 주소나 시/도청 이름으로 검색.

---

## 4. 설정값

`application.yml` / `application-dev.yml`에 이미 자리를 만들어 뒀다.

```yaml
tour-api:
  base-url: https://apis.data.go.kr/B551011/KorService2
  service-key: ${TOUR_API_SERVICE_KEY}

kakao-local:
  base-url: https://dapi.kakao.com/v2/local
  rest-api-key: ${KAKAO_LOCAL_API_KEY}
```

키 값은 `application.properties`(로컬 전용, `.gitignore`됨)에 있다. 실제 값은 이 문서에도, 코드 주석에도 남기지 않는다.

---

## 5. 활용신청한 TourAPI 기능 (참고)

공공데이터포털의 "한국관광공사_국문 관광정보 서비스_GW"(https://www.data.go.kr/data/15101578/openapi.do)
아래 8개를 신청해 뒀다 — 지금 스펙에 필요한 것만 고른 목록이다.

- 법정동코드조회 (`ldongCode2`)
- 지역기반 관광정보조회 (`areaBasedList2`)
- 키워드 검색 조회 (`searchKeyword2`)
- 위치기반 관광정보조회 (`locationBasedList2`) — `listNearbyPlaces`(PLAN-08)용
- 공통정보조회 (`detailCommon2`) — `getPlace`(PLAN-07) 상세 정보용
- 소개정보조회 (`detailIntro2`) — 이용시간·휴무일용
- 관광사진정보조회 (`detailImage2`) — `getPlace`(PLAN-07)의 `imageUrls`용(2026-09-19 확인, 이미 승인됨)
- 관광정보 동기화 목록 조회 (`areaBasedSyncList2`)

일일 호출 한도는 기능당 1,000회.
