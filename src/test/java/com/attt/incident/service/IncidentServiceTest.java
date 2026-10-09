package com.attt.incident.service;

import com.attt.incident.dto.IncidentCreateRequest;
import com.attt.incident.dto.IncidentResponse;
import com.attt.incident.dto.StatusUpdateRequest;
import com.attt.incident.entity.Incident;
import com.attt.incident.entity.IncidentCategory;
import com.attt.incident.entity.IncidentSeverity;
import com.attt.incident.entity.IncidentStatus;
import com.attt.incident.entity.IoC;
import com.attt.incident.entity.IoCStatus;
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
import org.springframework.security.access.AccessDeniedException;
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

    @Test
    void assignedAnalystCannotCloseAndClassifyIncident() {
        User analyst = User.builder().id(7L).username("analyst").email("analyst@example.com").build();
        Incident incident = Incident.builder().id(9L).status(IncidentStatus.RESOLVED)
                .reportedBy(mockUser).assignedTo(analyst).severity(IncidentSeverity.HIGH).build();
        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setNewStatus(IncidentStatus.CLOSED);
        request.setResolutionType(com.attt.incident.entity.ResolutionType.TRUE_POSITIVE);
        request.setNote("Xác nhận đóng sự cố");
        when(incidentRepository.findById(9L)).thenReturn(Optional.of(incident));
        when(authentication.getName()).thenReturn("analyst");
        when(authentication.getAuthorities()).thenAnswer(ignored ->
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
        when(userRepository.findByUsername("analyst")).thenReturn(Optional.of(analyst));

        assertThrows(AccessDeniedException.class,
                () -> incidentService.changeStatus(9L, request, authentication));
        verify(incidentRepository, never()).save(any());
        verify(auditService, never()).append(any(), any(), any(), any(), any(), any());
    }

    @Test
    void analystCanViewIncidentTheyReportedBeforeAssignment() {
        Incident incident = Incident.builder()
                .id(9L)
                .incidentCode("INC-2026-000009")
                .title("Sự cố do analyst khai báo")
                .description("Chi tiết")
                .status(IncidentStatus.NEW)
                .severity(IncidentSeverity.MEDIUM)
                .reportedBy(mockUser)
                .build();
        when(incidentRepository.findById(9L)).thenReturn(Optional.of(incident));
        when(authentication.getName()).thenReturn("testuser");
        when(authentication.getAuthorities()).thenAnswer(ignored ->
                List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));

        IncidentResponse response = incidentService.getIncident(9L, authentication);

        assertEquals(9L, response.getId());
        assertEquals("testuser", response.getReportedByUsername());
    }

    @Test
    void deletingRemovedIocAgainIsRejectedWithoutChangingAuditHistory() {
        Incident incident = Incident.builder().id(9L).status(IncidentStatus.INVESTIGATING)
                .reportedBy(mockUser).severity(IncidentSeverity.HIGH).build();
        IoC removed = IoC.builder().id(12L).incident(incident).status(IoCStatus.REMOVED).build();
        when(incidentRepository.findById(9L)).thenReturn(Optional.of(incident));
        when(iocRepository.findById(12L)).thenReturn(Optional.of(removed));
        when(authentication.getName()).thenReturn("testuser");
        when(authentication.getAuthorities()).thenAnswer(ignored ->
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));

        assertThrows(BadRequestException.class,
                () -> incidentService.deleteIoC(9L, 12L, authentication));

        verify(iocRepository, never()).save(any());
        verify(auditService, never()).append(any(), any(), any(), any(), any(), any());
    }
}
