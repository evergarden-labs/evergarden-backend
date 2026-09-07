# 실시간 공동 편집 프로토콜

> 최종 수정 2026-09-07 · 근거: ARCH-12 · ADR-028 · ADR-051
>
> **이 문서는 `evergardenapi.yaml`이 담을 수 없는 부분을 다룹니다.**
> OpenAPI는 HTTP 요청과 응답만 기술합니다. 아카이브를 여럿이 동시에 편집할 때
> 오가는 WebSocket 메시지는 여기서 정의합니다.
>
> 접속 정보를 받는 `GET /archives/{archiveId}/collaboration/session`은 명세에 있습니다.
> 이 문서는 그 뒤부터입니다.

---

## 1. 무엇을 실시간으로 주고받는가

**변경 사실만 주고받습니다. 저장은 하지 않습니다.**

편집은 기존 HTTP API로 저장합니다. WebSocket은 "누가 무엇을 바꿨다"를 다른 참여자에게
알리는 통로일 뿐입니다.

```
1. 지은이 사진을 옮긴다
2. 앱 → PATCH /archives/12/items/34   (HTTP · 저장)
3. 서버 → 토픽에 item.updated 발행    (WebSocket · 알림)
4. 민수의 앱이 받아서 화면을 갱신한다
```

**이 구조를 택한 이유** — 저장 경로가 하나면 검증·권한·오류 처리를 한 벌만 만들면 됩니다.
WebSocket으로도 저장하면 같은 규칙을 두 곳에 만들고 둘이 어긋나기 시작합니다.

메시지가 유실되어도 데이터는 이미 DB에 있습니다. 화면만 잠깐 어긋나고,
새로고침하면 맞춰집니다.

---

## 2. 연결

### 2.1 접속 정보 받기

```
GET /archives/{archiveId}/collaboration/session
→ { websocketUrl, topic, activeEditorCount }
```

편집 권한이 없으면 여기서 `403`입니다(`NOT_COLLABORATOR` · `COLLABORATION_CLOSED`).
**권한 검사는 이 단계에서 끝냅니다.**

### 2.2 STOMP 연결

STOMP over WebSocket을 씁니다(ADR-051). Spring의 `spring-boot-starter-websocket`이
그대로 지원하므로 별도 라이브러리가 필요 없습니다.

```
CONNECT
Authorization: Bearer <accessToken>
```

액세스 토큰을 CONNECT 프레임 헤더에 실습니다. 서버는 여기서 한 번 더 확인합니다 —
`GET .../session`을 호출한 뒤 권한이 사라졌을 수 있습니다(공동 편집 종료, 나가기, 차단).

토큰이 만료되면 서버가 연결을 끊습니다. 앱은 `POST /auth/token/refresh`로 재발급받고
다시 연결합니다.

### 2.3 구독

```
SUBSCRIBE
destination: /topic/archives/{archiveId}
```

한 아카이브가 한 토픽입니다. 다른 아카이브의 변경은 오지 않습니다.

---

## 3. 서버가 보내는 메시지

모든 메시지는 아래 봉투를 씁니다. HTTP 응답 봉투(`{data, meta}`)와 다릅니다 —
이쪽은 요청에 대한 응답이 아니라 일방적인 알림입니다.

```json
{
  "type": "item.updated",
  "archiveId": 12,
  "actor": { "userId": 7, "nickname": "지은" },
  "occurredAt": "2026-09-07T14:32:10+09:00",
  "payload": { }
}
```

| 필드 | 설명 |
|---|---|
| `type` | 아래 표의 이벤트 종류 |
| `actor` | 이 변경을 일으킨 사람. **자기 자신이 보낸 변경도 되돌아옵니다** |
| `occurredAt` | 서버가 처리한 시각 |
| `payload` | 종류마다 다름 |

> **자기 변경이 되돌아오는 것을 앱이 처리해야 합니다.**
> `actor.userId`가 나면 무시하거나, 서버 응답으로 화면을 확정하는 데 씁니다.
> 걸러 보내지 않는 이유는 서버가 "누가 보낸 요청인지"와 "누가 구독 중인지"를
> 맞춰봐야 해서 복잡해지기 때문입니다.

### 3.1 이벤트 목록

| `type` | 언제 | `payload` |
|---|---|---|
| `item.added` | 사진·영상을 배치했을 때 | 추가된 `ArchiveItem` 배열 |
| `item.updated` | 배치·순서·캡션을 바꿨을 때 | 바뀐 `ArchiveItem` 하나 |
| `item.removed` | 항목을 뺐을 때 | `{ "itemId": 34 }` |
| `layout.replaced` | 레이아웃을 일괄 변경했을 때 | 전체 `ArchiveItem` 배열 |
| `cover.changed` | 대표 사진을 바꿨을 때 | `{ "itemId": 34 }` |
| `archive.updated` | 이름·테마·대표 색상을 바꿨을 때 | 바뀐 필드만 |
| `collaboration.closed` | 공동 편집이 종료됐을 때 | 없음 |
| `editor.joined` | 누가 접속했을 때 | 접속한 사람의 `Collaborator` |
| `editor.left` | 누가 나갔을 때 | `{ "userId": 7 }` |

`ArchiveItem`·`Collaborator`의 모양은 `evergardenapi.yaml`의 스키마와 **같습니다.**
같은 것을 두 번 정의하지 않습니다.

### 3.2 `collaboration.closed`를 받으면

앱은 편집 UI를 닫고 조회 모드로 바꿉니다. 종료 후에도 **조회는 됩니다**(ADR-017).
이후 편집 요청은 HTTP에서 `COLLABORATION_CLOSED`로 막힙니다.

---

## 4. 클라이언트가 보내는 메시지

**변경을 보내는 메시지는 없습니다.** 편집은 전부 HTTP입니다.

클라이언트가 STOMP로 보내는 것은 연결 유지뿐입니다.

```
SEND
destination: /app/archives/{archiveId}/ping
```

30초마다 보냅니다. 60초 동안 없으면 서버가 나간 것으로 보고 `editor.left`를 발행합니다.
브라우저나 앱이 죽어 연결이 끊긴 것을 감지하기 위한 것입니다.

---

## 5. 접속한 사람 보여주기

`editor.joined` · `editor.left`로 목록을 관리합니다.

접속 직후 현재 목록은 **`GET /archives/{archiveId}/collaboration/session`의
응답에 담긴 것을 씁니다.** 구독을 시작한 뒤에는 이벤트로 갱신합니다.

> **한 사람이 두 기기로 접속하면 목록에 한 번만 보입니다.**
> `userId` 기준으로 셉니다. `editor.left`는 그 사람의 마지막 연결이 끊길 때만 발행합니다.

---

## 6. 재접속

연결이 끊기면 **아카이브 전체를 다시 받습니다**(ADR-051).

```
1. 연결이 끊긴다
2. GET /archives/{archiveId}   ← 현재 상태를 통째로
3. 화면을 그 상태로 다시 그린다
4. 다시 SUBSCRIBE
```

서버는 변경 이력을 보관하지 않습니다. 시퀀스 번호도 없습니다.

**이 방식을 택한 이유** — 아카이브 항목이 수십 개 수준이라 통째로 받아도 가볍습니다.
이력을 보관하면 얼마나 오래 둘지, 보관 기간을 넘긴 재접속은 어떻게 할지가
새 문제로 따라옵니다. 어차피 그때도 전체를 다시 받아야 합니다.

재연결은 **지수 백오프**로 시도합니다 — 1초, 2초, 4초, 8초, 최대 30초.
서버가 잠깐 재시작할 때 모든 클라이언트가 동시에 몰리는 것을 막습니다.

---

## 7. 충돌

**마지막 쓰기가 이깁니다**(ADR-028). 잠금을 두지 않습니다.

두 사람이 같은 사진을 동시에 옮기면 나중에 저장된 위치가 남습니다.
먼저 옮긴 사람의 화면도 `item.updated`를 받아 나중 위치로 바뀝니다.

**앱이 해야 할 것** — 내가 방금 옮긴 사진이 남의 변경으로 되돌아오면,
그 사실을 사용자가 알아채게 해야 합니다. 말없이 바뀌면 "내가 잘못 옮겼나?" 하게 됩니다.
접속자 목록(5절)이 보이면 "민수가 같이 편집 중"이라는 맥락이 있어 납득이 됩니다.

---

## 8. 정하지 않은 것

| 항목 | 상태 |
|---|---|
| 커서·선택 상태 공유 | 안 합니다. "민수가 이 사진을 보고 있음" 같은 표시는 없습니다 |
| 편집 이력·되돌리기 | 없습니다. 스토리에 없고, 이력을 남기려면 별도 테이블이 필요합니다 |
| 동시 접속 인원 상한 | 두지 않았습니다. 동행자 규모(2~4명)에서 문제되지 않습니다 |
| 메시지 크기 제한 | `layout.replaced`가 항목 수에 비례해 커집니다. 항목이 수백 개가 되면 다시 봅니다 |

---

## 9. 구현 메모

- `spring-boot-starter-websocket`은 이미 `build.gradle`에 있습니다
- `@MessageMapping`으로 `ping`을 받고, `SimpMessagingTemplate`으로 토픽에 발행합니다
- **HTTP 컨트롤러가 저장한 뒤 이벤트를 발행합니다.** 서비스 계층에서 발행하면
  트랜잭션이 커밋되기 전에 알림이 나갈 수 있습니다 — `@TransactionalEventListener`를 쓰거나
  커밋 후에 발행합니다
- 서버를 여러 대로 늘리면 인스턴스 간 메시지 전달이 필요합니다.
  Redis가 이미 있으므로 STOMP 브로커 릴레이 대신 **Redis Pub/Sub**으로 이어붙이는 편이 간단합니다
