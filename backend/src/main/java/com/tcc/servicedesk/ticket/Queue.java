package com.tcc.servicedesk.ticket;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "queues")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Queue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
