package org.di.digital.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.model.enums.UserSettingsDetalizationLevel;
import org.di.digital.model.enums.UserSettingsLanguage;
import org.di.digital.model.enums.UserSettingsTheme;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Slf4j
@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "user_settings")
@EntityListeners(AuditingEntityListener.class)
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "detail_level")
    private UserSettingsDetalizationLevel level = UserSettingsDetalizationLevel.HIGH;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 10)
    private UserSettingsLanguage language = UserSettingsLanguage.RU;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "theme", length = 10)
    private UserSettingsTheme theme = UserSettingsTheme.LIGHT;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;
}
