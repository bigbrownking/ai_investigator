package org.di.digital.model.user;

import jakarta.persistence.*;
import lombok.*;
import org.di.digital.model.Localizable;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "regions")
public class Region implements Localizable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ru_name")
    private String ruName;

    @Column(name = "kz_name")
    private String kzName;

    @OneToMany(mappedBy = "region", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<User> users;

    @Column(name = "map_code")
    private String mapCode;

    @OneToMany(mappedBy = "region", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Address> addresses;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "region_admins",
            joinColumns = @JoinColumn(name = "region_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> admins = new ArrayList<>();
}
