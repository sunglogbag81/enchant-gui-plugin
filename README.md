# EnchantGUI

Paper 1.20.1 서버용 GUI 인챈트 플러그인입니다.

## 핵심 기능

- `/인챈트` GUI 오픈
- 좌측 장비 슬롯 + 우측 강화권 슬롯 구조
- 강화권별 성공 확률 설정
- 책 / 종이 / 커스텀 아이템 형태의 강화권 지급
- PDC 기반 진짜 강화권 판별
- 필요하면 display-name fallback 허용 가능
- 다중 인챈트 강화권 지원
- 확률 증가 아이템(booster) 지원
- 보호권 지원
- 실패 패널티 개별 설정
- 사운드 / 파티클 on/off
- flat file / SQLite 로그 저장
- PlaceholderAPI 연동

## 명령어

- `/인챈트` : GUI 열기
- `/인챈트 reload` : 설정 리로드
- `/인챈트 list` : 토큰 / 부스터 / 보호권 키 목록 표시
- `/인챈트 give <플레이어> <token|booster|protection> <key> [amount]`

## 권한

- `enchantgui.use`
- `enchantgui.admin`
- `enchantgui.reload`
- `enchantgui.give`
- `enchantgui.list`

## 기본 GUI 구조

- 장비 슬롯: `11`
- 강화권 슬롯: `15`
- 보정 아이템 슬롯: `20`
- 보호권 슬롯: `24`
- 미리보기 슬롯: `13`
- 강화 버튼 슬롯: `22`

모든 슬롯은 `config.yml`에서 수정 가능합니다.

## 강화권 설계

기본 예시는 아래처럼 들어 있습니다.

- `sharpness_1`
- `sharpness_3_risky`
- `protection_combo`

각 강화권은 아래 값을 가질 수 있습니다.

- `material`
- `display-name`
- `lore`
- `chance`
- `operation: SET | ADD`
- `target-groups`
- `target-materials`
- `failure.destroy-item-on-fail`
- `failure.remove-target-enchants-on-fail`
- `failure.downgrade-target-enchants-on-fail`
- `enchants[]`

## 예시 지급

```text
/인챈트 give 감파르다 token sharpness_1 3
/인챈트 give 감파르다 booster lucky_dust_big 5
/인챈트 give 감파르다 protection basic_guard 2
```

## PlaceholderAPI

PlaceholderAPI가 설치되어 있으면 아래 플레이스홀더를 사용할 수 있습니다.

- `%enchantgui_last_result%`
- `%enchantgui_last_token%`
- `%enchantgui_last_booster%`
- `%enchantgui_last_base_chance%`
- `%enchantgui_last_bonus_chance%`
- `%enchantgui_last_final_chance%`

## 로그

`plugins/EnchantGUI/` 경로 아래에 저장됩니다.

- `attempts.log`
- `enchantgui.db`

둘 다 `config.yml`에서 on/off 가능합니다.

## 빌드

```bash
mvn package
```

빌드 결과물:

```text
target/EnchantGUI-1.0.0.jar
```

## 주의 사항

- 서버 버전은 Paper 1.20.1 기준으로 맞췄습니다.
- 진짜 강화권만 허용하려면 `require-pdc-token: true` 상태를 유지하세요.
- 플레이어가 직접 이름을 바꾼 종이도 허용하려면 `allow-plain-name-fallback: true`로 바꾸면 됩니다.
- `allow-unsafe-enchants`와 `allow-over-vanilla-max-level`을 켜면 바닐라 제한을 넘어서는 세팅도 가능합니다.
