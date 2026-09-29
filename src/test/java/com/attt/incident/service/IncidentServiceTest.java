package com.attt.incident.service;

import com.attt.incident.dto.IncidentCreateRequest;
import com.attt.incident.dto.IncidentResponse;
import com.attt.incident.dto.StatusUpdateRequest;
import com.attt.incident.entity.Incident;
import com.attt.incident.entity.IncidentCategory;
import com.attt.incident.entity.IncidentSeverity;
import com.attt.incident.entity.IncidentStatus;
import com.attt.incident.entity.User;
import com.attt.incident.repository.IncidentCategoryRepository;
import com.attt.incident.repository.IncidentRepository;
import com.attt.incident.repository.IncidentTaskRepository;
import com.attt.incident.repository.IoCRepository;
import com.attt.incident.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.attt.incident.exception.BadRequestException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentCategoryRepository categoryRepository;
    @Mock
    private IncidentAuditService auditService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IoCRepository iocRepository;
    @Mock
    private IncidentTaskRepository taskRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private WebSocketNotificationService wsNotificationService;

    @InjectMocks
    private IncidentService incidentService;

    @Mock
    private Authentication authentication;

    private User mockUser;
    private IncidentCategory mockCategory;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).username("testuser").email("test@example.com").build();
        mockCategory = IncidentCategory.builder().id(1L).name("Network").build();
    }

    @Test
    void createIncident_Success() {
        // Arrange
        IncidentCreateRequest request = new IncidentCreateRequest();
        request.setTitle("Mất kết nối mạng");
        request.setDescription("Server không thể ping ra ngoài");
        request.setSeverity(IncidentSeverity.HIGH);
        request.setCategoryId(1L);

        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(mockCategory));
        when(incidentRepository.nextIncidentCodeSequence()).thenReturn(6L);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        IncidentResponse response = incidentService.createIncident(request, authentication);

        // Assert
        assertNotNull(response);
        assertEquals("Mất kết nối mạng", response.getTitle());
        assertEquals(IncidentSeverity.HIGH, response.getSeverity());
        assertTrue(response.getIncidentCode().contains("INC-"));

        verify(incidentRepository, times(1)).save(any(Incident.class));
        verify(auditService, times(1)).append(any(), any(), eq("CREATE"), isNull(), eq("NEW"), anyString());
        verify(emailService, times(1)).sendEmail(anyString(), anyString(), anyString());
        verify(wsNotificationService, times(1)).notifyIncidentUpdate(any(IncidentResponse.class));
    }

    @Test
    void containmentRequiresOperationalNote() {
        Incident incident = Incident.builder().id(9L).status(IncidentStatus.INVESTIGATING)
                .reportedBy(mockUser).severity(IncidentSeverity.HIGH).build();
        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setNewStatus(IncidentStatus.CONTAINED);
        when(incidentRepository.findById(9L)).thenReturn(Optional.of(incident));
        when(authentication.getName()).thenReturn("testuser");
        when(authentication.getAuthorities()).thenAnswer(ignored ->
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));

        assertThrows(BadRequestException.class,
                () -> incidentService.changeStatus(9L, request, authentication));
        verify(incidentRepository, never()).save(any());
    }
}
