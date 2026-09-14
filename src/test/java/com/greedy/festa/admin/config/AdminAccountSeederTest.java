package com.greedy.festa.admin.config;

import com.greedy.festa.admin.entity.AdminUser;
import com.greedy.festa.admin.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class AdminAccountSeederTest {

    private static final String 관리자_이름 = "admin";
    private static final String 원문_비밀번호 = "festa-admin-1234";
    private static final String 공백뿐인_값 = "   ";

    private final ApplicationArguments 부팅_인자 = new DefaultApplicationArguments();
    private final PasswordEncoder 실제_인코더 = new BCryptPasswordEncoder();

    @Mock
    private AdminUserRepository adminUserRepository;

    @Captor
    private ArgumentCaptor<AdminUser> 저장된_관리자;

    @Test
    void 아이디만_주면_부팅에_실패한다() {
        AdminAccountSeeder seeder = seeder(관리자_이름, "");

        assertThatThrownBy(() -> seeder.run(부팅_인자))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_INITIAL_PASSWORD");

        verifyNoInteractions(adminUserRepository);
    }

    @Test
    void 비밀번호만_주면_부팅에_실패한다() {
        AdminAccountSeeder seeder = seeder("", 원문_비밀번호);

        assertThatThrownBy(() -> seeder.run(부팅_인자))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_INITIAL_USERNAME");

        verifyNoInteractions(adminUserRepository);
    }

    @Test
    void 공백뿐인_아이디는_없는_것으로_보고_부팅에_실패한다() {
        AdminAccountSeeder seeder = seeder(공백뿐인_값, 원문_비밀번호);

        assertThatThrownBy(() -> seeder.run(부팅_인자))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_INITIAL_USERNAME");

        verifyNoInteractions(adminUserRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", 공백뿐인_값})
    void 둘_다_비어_있으면_시딩을_건너뛴다(String 빈_값) throws Exception {
        AdminAccountSeeder seeder = seeder(빈_값, 빈_값);

        seeder.run(부팅_인자);

        verify(adminUserRepository, never()).save(any());
        verify(adminUserRepository, never()).existsByUsername(any());
    }

    @Test
    void 같은_아이디의_계정이_이미_있으면_저장하지_않는다() throws Exception {
        given(adminUserRepository.existsByUsername(관리자_이름)).willReturn(true);
        AdminAccountSeeder seeder = seeder(관리자_이름, 원문_비밀번호);

        seeder.run(부팅_인자);

        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void 계정이_없으면_비밀번호를_해시로_바꿔_저장한다() throws Exception {
        given(adminUserRepository.existsByUsername(관리자_이름)).willReturn(false);
        AdminAccountSeeder seeder = seeder(관리자_이름, 원문_비밀번호);

        seeder.run(부팅_인자);

        verify(adminUserRepository).save(저장된_관리자.capture());
        AdminUser 관리자 = 저장된_관리자.getValue();
        assertSoftly(softly -> {
            softly.assertThat(관리자.getUsername()).isEqualTo(관리자_이름);
            softly.assertThat(관리자.getPasswordHash()).isNotEqualTo(원문_비밀번호);
            softly.assertThat(실제_인코더.matches(원문_비밀번호, 관리자.getPasswordHash())).isTrue();
        });
    }

    private AdminAccountSeeder seeder(String 아이디, String 비밀번호) {
        return new AdminAccountSeeder(adminUserRepository, 실제_인코더, 아이디, 비밀번호);
    }
}
