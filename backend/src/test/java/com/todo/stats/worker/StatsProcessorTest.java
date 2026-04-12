package com.todo.stats.worker;

import com.todo.stats.domain.WeeklyGoal;
import com.todo.stats.domain.WeeklyStat;
import com.todo.publisher.TodoEventPublisher;
import com.todo.stats.infrastructure.WeeklyGoalRepository;
import com.todo.stats.infrastructure.WeeklyStatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatsProcessorTest {

    @Mock
    private WeeklyStatRepository weeklyStatRepository;

    @Mock
    private WeeklyGoalRepository weeklyGoalRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private StatsProcessor statsProcessor;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    /**
     * Redis Streams 메시지를 흉내 낸 MapRecord Mock을 생성한다.
     * eventType, userId, eventDate(오늘)만 지정하면 된다.
     */
    private MapRecord<String, Object, Object> record(String eventType, Long userId) {
        Map<Object, Object> body = new HashMap<>();
        body.put("eventType", eventType);
        body.put("userId", userId.toString());
        body.put("todoId", "100");
        body.put("eventDate", LocalDate.now().toString());

        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getValue()).thenReturn(body);
        when(record.getStream()).thenReturn(TodoEventPublisher.STREAM_KEY);
        when(record.getId()).thenReturn(RecordId.of("1-0"));
        return record;
    }

    /** 주간 목표가 goalCount인 WeeklyGoal을 반환한다. */
    private WeeklyGoal goal(int goalCount) {
        WeeklyGoal g = new WeeklyGoal();
        g.setGoalCount(goalCount);
        return g;
    }

    /** completedCount와 goalAchieved를 가진 기존 WeeklyStat을 반환한다. */
    private WeeklyStat existingStat(int completedCount, boolean goalAchieved) {
        WeeklyStat stat = new WeeklyStat();
        stat.setUserId(1L);
        stat.setCompletedCount(completedCount);
        stat.setGoalAchieved(goalAchieved);
        return stat;
    }

    // ─── 테스트 ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DB 저장 실패 시 ACK 미발행")
    class AckOnFailure {

        @Test
        @DisplayName("weeklyStatRepository.save()가 예외를 던지면 ACK를 보내지 않는다")
        void shouldNotAck_whenSaveFails() {
            // given
            when(weeklyStatRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(existingStat(2, false)));
            when(weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(goal(3)));
            when(weeklyStatRepository.save(any())).thenThrow(new RuntimeException("DB 연결 실패"));

            // when
            statsProcessor.processWeeklyStats(record("COMPLETED", 1L));

            // then
            // PEL에 남겨야 하므로 acknowledge가 호출되어선 안 된다
            verify(streamOperations, never())
                    .acknowledge(anyString(), anyString(), any(RecordId.class));
        }

        @Test
        @DisplayName("처리 대상이 아닌 이벤트는 저장 없이 즉시 ACK한다")
        void shouldAckImmediately_whenEventIsNotSupported() {
            // when
            // INIT 는 처리대상이 아닌 이벤트를 의미함
            statsProcessor.processWeeklyStats(record("INIT", 1L));

            // then
            // DB 저장 없이 ACK만 발행되어야 한다
            verify(weeklyStatRepository, never()).save(any());
            verify(streamOperations).acknowledge(
                    eq(TodoEventPublisher.STREAM_KEY),
                    eq(TodoEventPublisher.WEEKLY_GROUP),
                    any(RecordId.class));
        }
    }

    @Nested
    @DisplayName("UNCOMPLETED 시 completedCount 하한 보호")
    class UncompletedFloor {

        @Test
        @DisplayName("completedCount가 이미 0일 때 UNCOMPLETED가 와도 0으로 유지된다")
        void completedCount가_0일때_undo해도_0_유지() {
            // given — 이미 completedCount = 0인 상태
            when(weeklyStatRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(existingStat(0, false)));
            when(weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(goal(3)));

            ArgumentCaptor<WeeklyStat> captor = ArgumentCaptor.forClass(WeeklyStat.class);
            when(weeklyStatRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            // when
            statsProcessor.processWeeklyStats(record("UNCOMPLETED", 1L));

            // then
            assertThat(captor.getValue().getCompletedCount())
                    .as("0 미만으로 내려가면 안 된다")
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("completedCount가 1일 때 UNCOMPLETED가 오면 0이 된다")
        void completedCount가_1일때_undo하면_0() {
            // given
            when(weeklyStatRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(existingStat(1, false)));
            when(weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(goal(3)));

            ArgumentCaptor<WeeklyStat> captor = ArgumentCaptor.forClass(WeeklyStat.class);
            when(weeklyStatRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            // when
            statsProcessor.processWeeklyStats(record("UNCOMPLETED", 1L));

            // then
            assertThat(captor.getValue().getCompletedCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("goalAchieved 재평가")
    class GoalAchievedReeval {

        @Test
        @DisplayName("COMPLETED로 completedCount가 goal에 도달하면 goalAchieved가 true가 된다")
        void completed_시_목표달성하면_goalAchieved_true() {
            // given — completedCount=2, goal=3 → 이번 완료로 3 달성
            when(weeklyStatRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(existingStat(2, false)));
            when(weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(goal(3)));

            ArgumentCaptor<WeeklyStat> captor = ArgumentCaptor.forClass(WeeklyStat.class);
            when(weeklyStatRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            // when
            statsProcessor.processWeeklyStats(record("COMPLETED", 1L));

            // then
            WeeklyStat saved = captor.getValue();
            assertThat(saved.getCompletedCount()).isEqualTo(3);
            assertThat(saved.isGoalAchieved()).isTrue();
        }

        @Test
        @DisplayName("UNCOMPLETED로 completedCount가 goal 미만이 되면 goalAchieved가 false가 된다")
        void uncompleted_시_목표미달이면_goalAchieved_false() {
            // given — completedCount=3, goal=3, goalAchieved=true → undo 후 2로 미달
            when(weeklyStatRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(existingStat(3, true)));
            when(weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(goal(3)));

            ArgumentCaptor<WeeklyStat> captor = ArgumentCaptor.forClass(WeeklyStat.class);
            when(weeklyStatRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            // when
            statsProcessor.processWeeklyStats(record("UNCOMPLETED", 1L));

            // then
            WeeklyStat saved = captor.getValue();
            assertThat(saved.getCompletedCount()).isEqualTo(2);
            assertThat(saved.isGoalAchieved()).isFalse();
        }

        @Test
        @DisplayName("목표 설정이 없으면 기본값 3을 기준으로 goalAchieved를 평가한다")
        void 목표미설정_시_기본값_3으로_평가() {
            // given — completedCount=2 → COMPLETED → 3 → 기본 goal=3 달성
            when(weeklyStatRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(existingStat(2, false)));
            when(weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.empty()); // 목표 미설정

            ArgumentCaptor<WeeklyStat> captor = ArgumentCaptor.forClass(WeeklyStat.class);
            when(weeklyStatRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            // when
            statsProcessor.processWeeklyStats(record("COMPLETED", 1L));

            // then
            assertThat(captor.getValue().isGoalAchieved()).isTrue();
        }

        @Test
        @DisplayName("성공 시 ACK가 한 번 정확히 발행된다")
        void 성공_시_ack_발행() {
            // given
            when(weeklyStatRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(existingStat(1, false)));
            when(weeklyGoalRepository.findByUserIdAndYearAndWeekNumber(any(), anyInt(), anyInt()))
                    .thenReturn(Optional.of(goal(3)));
            when(weeklyStatRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // when
            statsProcessor.processWeeklyStats(record("COMPLETED", 1L));

            // then
            verify(streamOperations, times(1))
                    .acknowledge(
                            eq(TodoEventPublisher.STREAM_KEY),
                            eq(TodoEventPublisher.WEEKLY_GROUP),
                            any(RecordId.class));
        }
    }
}