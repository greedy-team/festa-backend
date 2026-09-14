# #166 JVM 기동 시간 실험 — 판정 대기

2026-09-14 확인. 선행 #156, #164, #165는 머지되었다. 후보는
`-XX:TieredStopAtLevel=1` 하나다. 현재 기본값은 미적용이며, 적용 후 실측은 아직 없다.

## 적용 전 기록

[배포 34690469768](https://github.com/greedy-team/festa-backend/actions/runs/34690469768)의
동일 커밋 `0db4738393e84ff2a8958342cd260f500a452ef3`, development, 성공한 6개 attempt다.
각 attempt 로그의 `Container …-app-1 Started`부터 `Healthy`까지를 계산했다.
이는 Spring의 내부 기동 시간이 아니라 Docker의 정상 판정 대기 시간이다.
헬스체크 주기와 Actions 로그 전달 지연도 포함되므로 밀리초 차이에 의미를 두지 않는다.

| attempt | Started (UTC) | Healthy (UTC) | 정상 판정 대기(초) | 재기동 스텝(초) |
| --- | --- | --- | ---: | ---: |
| 1 | 09-12 11:14:53.757 | 09-12 11:16:33.766 | 120.009 | 114 |
| 2 | 09-13 01:20:12.195 | 09-13 01:21:44.200 | 92.005 | 105 |
| 3 | 09-13 01:44:31.468 | 09-13 01:46:22.975 | 111.507 | 129 |
| 4 | 09-13 02:03:30.591 | 09-13 02:05:35.261 | 124.670 | 137 |
| 5 | 09-13 02:19:46.497 | 09-13 02:21:31.190 | 104.693 | 117 |
| 6 | 09-13 10:04:55.580 | 09-13 10:06:40.076 | 104.496 | 118 |

6건 중앙값은 108.100초, 범위는 92.005~124.670초(폭 32.665초)다.
최초 GHCR 배포 attempt 1을 별도 분류한 반복 배포 5건의 중앙값은 104.693초다.
재기동 스텝 중앙값은 117.5초다. attempt 1은 로그 구간이 API 스텝 시간보다 길어
서로 다른 시계를 혼용해서는 안 된다. 서버 측 시간으로 새 기준군을 확보하면 더 정확하다.
같은 SHA여도 빌드의 베이스 이미지 digest, 서버 부하·캐시까지 같다는 보장은 없다.

원본 확인(각 attempt를 명시해야 마지막 재실행만 보게 되는 일을 피한다):

```sh
gh run view 34690469768 --repo greedy-team/festa-backend --attempt 2 --log
gh api repos/greedy-team/festa-backend/actions/runs/34690469768/attempts/2/jobs
```

## 후보 적용과 복구

변경된 CD가 반영된 뒤 GitHub `development` Environment 변수
`STARTUP_JVM_TRIAL=true`를 설정하면 다음 평소 배포부터 후보를 적용한다.
변수 미설정/false는 기존 설정이다. production은 true여도 적용되지 않는다.
CD는 선택한 값을 로그에 남기고 `app.env` → Compose의 `JAVA_TOOL_OPTIONS`로 전달한다.
이미지의 ENTRYPOINT, 앱 코드, HEALTHCHECK와 배포 판정 예산은 그대로다.

실패 롤백은 `app.env`에서 실험 옵션을 삭제한 뒤 이전 이미지를 띄운다.
다음 배포에서 다시 적용되지 않게 GitHub 변수도 false로 바꾼다.
효과가 없을 때도 변수를 false로 바꾸고 다음 배포에서 빈 옵션으로 재생성됐는지 확인한다.
변수 변경만으로 실행 중인 JVM 설정이 바뀌지는 않는다.

## 표본과 판정

1. 실제 컨테이너의 `JAVA_TOOL_OPTIONS`와 CD 선택 로그가 일치하는지 확인한다.
2. 같은 development 서버에서 정상 배포 최소 5건을 수집한다. run ID/attempt/SHA,
   이미지 digest, 옵션, Started/Healthy, 성공 여부, 서버 부하 이상을 함께 기록한다.
   실패·롤백·재시작 반복은 성공 시간 표본에서 분리하고 장애 결과로 남긴다.
3. 같은 지표의 중앙값을 비교한다. 최초 배포는 별도로 두고, 앱/JDK/서버 조건이
   달라지면 기준군을 다시 수집한다. 과거 표본만으로 인과 효과가 입증되는 것은 아니다.
4. 이번 보수적 판정 기준은 중앙값 절감이 기존 범위 폭 32.665초를 넘고,
   API 오류·응답 시간 악화가 없어야 채택 후보로 본다. 편차 안이면 원복한다.
   이 임계값은 통계적 유의성 검정이 아니라 이슈의 보수적 채택 방침을 수치화한 것이다.
5. 기동만 빨라졌다고 채택하지 않는다. C1 단계 제한은 장시간 실행의 최적화를
   줄일 수 있으므로 대표 API의 동일 부하 응답 시간도 비교한다.

근거: [Oracle의 tiered compilation 설명](https://docs.oracle.com/en/java/javase/21/vm/java-hotspot-virtual-machine-performance-enhancements.html),
[Spring의 후보 옵션 사용 예](https://docs.spring.io/spring-cloud-function/reference/adapters/aws-intro.html).
예제의 다른 옵션은 가져오지 않는다. GC/힙 고정, 스왑, 지연 초기화, CDS는 이슈 범위에서 제외한다.

## 현재 완료 범위

- 선행 작업 확인, 적용 전 표본 6건 수집 완료.
- 기본 미적용·개발 환경 전용 후보 스위치와 실패 롤백 구현.
- 적용 후 표본과 최종 채택 판정은 미완료. 측정 없이 이슈를 닫지 않는다.
